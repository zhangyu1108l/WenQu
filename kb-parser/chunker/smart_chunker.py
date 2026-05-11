"""
Smart document chunker for Step 4-D.

Three-level fallback decision flow:

    Has headings? -> yes -> chunk by heading
          |
          no
          v
    Has paragraphs? -> yes -> chunk by paragraph
          |
          no
          v
    Length fallback
"""

from typing import List, Optional

from models.schema import ChunkModel


# 三级降级决策流程：
#   有标题？ -> 是 -> 标题分块
#       |
#       否
#       v
#   有段落？ -> 是 -> 段落分块
#       |
#       否
#       v
#   长度兜底
class SmartChunker:
    """
    Smart chunker with a three-level fallback strategy.

    The strategy prefers semantic boundaries first, then falls back to weaker
    structure only when stronger structure is unavailable:
    heading chunks preserve the most complete document semantics; paragraph
    chunks are the next best unit; length chunks make unstructured text safe to
    index and retrieve.
    """

    TOKEN_TO_CHARS = 1.5

    def __init__(
        self,
        max_tokens: int = 512,
        overlap_tokens: int = 50,
        min_chunk_chars: int = 50,
    ) -> None:
        """
        Initialize chunking parameters.

        Args:
            max_tokens: Approximate maximum token count for a single chunk.
            overlap_tokens: Approximate overlapping token count for length
                fallback chunks.
            min_chunk_chars: Minimum character count retained after chunking.

        Notes:
            Token count is approximated with character count because accurate
            token calculation requires a tokenizer dependency. For Chinese
            documents, the approximation of 1 token ~= 1.5 Chinese characters
            is good enough for chunk sizing and keeps this parser lightweight.
        """
        self.max_tokens = max_tokens
        self.overlap_tokens = overlap_tokens
        self.min_chunk_chars = min_chunk_chars

    def chunk(
        self,
        text_blocks: List[dict],
        tables: List[dict],
        file_type: str,
    ) -> List[ChunkModel]:
        """
        Chunk parsed text blocks and tables into ChunkModel objects.

        Args:
            text_blocks: Structured text blocks from PDF or Word parsers.
            tables: Structured table blocks from PDF or Word parsers.
            file_type: Source file type, such as "pdf" or "docx".

        Returns:
            List[ChunkModel]: Ordered chunk list with chunk_index reset from 0.

        Notes:
            The fallback priority is heading -> paragraph -> length. Heading
            chunks preserve the richest semantic structure; paragraphs are a
            natural reading unit when headings are unavailable; length fallback
            protects retrieval quality for plain text without visible structure.
            Tables are always merged afterward as whole chunks, because a table
            is an independent semantic unit and should not participate in text
            fallback decisions.
        """
        normalized_file_type = (file_type or "").strip().lower()

        if self._has_heading_structure(text_blocks):
            chunks = self._chunk_by_heading(text_blocks)
        elif self._has_paragraph_structure(text_blocks, normalized_file_type):
            chunks = self._chunk_by_paragraph(text_blocks)
        else:
            text = self._join_block_texts(text_blocks, separator="\n")
            chunks = self._chunk_by_length(text)

        for table in tables:
            chunks.append(self._table_to_chunk(table, len(chunks)))

        # 文本块过小可丢弃；表格整表一个 chunk，不因 min_chunk_chars 误删短表
        chunks = [
            chunk
            for chunk in chunks
            if chunk.content and (
                len(chunk.content.strip()) >= self.min_chunk_chars
                or chunk.chunk_type == "table"
            )
        ]

        chunks.sort(key=lambda chunk: (
            chunk.page_no is None,
            chunk.page_no if chunk.page_no is not None else 0,
            chunk.chunk_index,
        ))

        return [
            self._make_chunk(
                content=chunk.content,
                chunk_index=index,
                chunk_type=chunk.chunk_type,
                heading_path=chunk.heading_path,
                page_no=chunk.page_no,
            )
            for index, chunk in enumerate(chunks)
        ]

    def _has_heading_structure(self, text_blocks: List[dict]) -> bool:
        """
        Determine whether the document has a usable heading structure.

        Args:
            text_blocks: Parsed text blocks.

        Returns:
            bool: True when at least two heading-like blocks are present.

        Notes:
            The threshold is 2 because a single heading may only be the document
            title. At least two headings are needed to indicate real structure.
        """
        heading_count = sum(1 for block in text_blocks if self._is_heading(block))
        return heading_count >= 2

    def _has_paragraph_structure(
        self,
        text_blocks: List[dict],
        file_type: str = "",
    ) -> bool:
        """
        Determine whether the document has paragraph separators.

        Args:
            text_blocks: Parsed text blocks.

        Returns:
            bool: True when paragraph boundaries are available.

        Notes:
            A double newline is a common paragraph separator in extracted text,
            so its presence indicates paragraph-level structure. Word parsing
            already emits one block per paragraph, so multiple Word text blocks
            are also treated as paragraph structure.
        """
        text = self._join_block_texts(text_blocks, separator="\n")
        if "\n\n" in text:
            return True

        return file_type == "docx" and sum(
            1 for block in text_blocks if self._get_text(block)
        ) >= 2

    def _chunk_by_heading(self, text_blocks: List[dict]) -> List[ChunkModel]:
        """
        Chunk text by heading boundaries.

        Args:
            text_blocks: Parsed text blocks with heading metadata.

        Returns:
            List[ChunkModel]: Chunks whose boundaries start at headings.

        Notes:
            The heading text itself is included in chunk content; otherwise the
            chunk would lose its most important local context. Very long heading
            chunks are recursively split by length because some sections contain
            much more text than the embedding/indexing target size.
        """
        chunks: List[ChunkModel] = []
        current_texts: List[str] = []
        current_heading_path: Optional[str] = None
        current_page_no: Optional[int] = None

        for block in text_blocks:
            text = self._get_text(block)
            if not text:
                continue

            if self._is_heading(block):
                chunks.extend(self._flush_heading_chunk(
                    current_texts,
                    current_heading_path,
                    current_page_no,
                    len(chunks),
                ))
                current_texts = []
                current_heading_path = block.get("heading_path") or text
                current_page_no = block.get("page_no")

            if not current_texts:
                current_page_no = current_page_no or block.get("page_no")
                current_heading_path = current_heading_path or block.get("heading_path")

            current_texts.append(text)

        chunks.extend(self._flush_heading_chunk(
            current_texts,
            current_heading_path,
            current_page_no,
            len(chunks),
        ))
        return chunks

    def _chunk_by_paragraph(self, text_blocks: List[dict]) -> List[ChunkModel]:
        """
        Chunk text by paragraph separators.

        Args:
            text_blocks: Parsed text blocks.

        Returns:
            List[ChunkModel]: Paragraph-based chunks.

        Notes:
            Short paragraphs are merged with the next paragraph to avoid
            fragmented chunks, which often hurts retrieval quality because tiny
            chunks lack enough context to match user questions well.
        """
        text = self._join_block_texts(text_blocks, separator="\n\n")
        paragraphs = [
            paragraph.strip()
            for paragraph in text.split("\n\n")
            if paragraph.strip()
        ]

        chunks: List[ChunkModel] = []
        buffer = ""

        for paragraph in paragraphs:
            if buffer:
                paragraph = f"{buffer}\n\n{paragraph}"
                buffer = ""

            if len(paragraph) < self.min_chunk_chars:
                buffer = paragraph
                continue

            if len(paragraph) > self._max_chars():
                split_chunks = self._chunk_by_length(paragraph)
                chunks.extend(self._reindex_chunks(split_chunks, len(chunks)))
                continue

            chunks.append(self._make_chunk(
                content=paragraph,
                chunk_index=len(chunks),
                chunk_type="paragraph",
                heading_path=None,
                page_no=None,
            ))

        if buffer:
            chunks.append(self._make_chunk(
                content=buffer,
                chunk_index=len(chunks),
                chunk_type="paragraph",
                heading_path=None,
                page_no=None,
            ))

        return chunks

    def _chunk_by_length(
        self,
        text: str,
        heading_path: str = None,
        page_no: int = None,
    ) -> List[ChunkModel]:
        """
        Chunk text by length using a sliding window.

        Args:
            text: Raw text to split.
            heading_path: Optional heading path copied to generated chunks.
            page_no: Optional page number copied to generated chunks.

        Returns:
            List[ChunkModel]: Length-based chunks.

        Notes:
            max_chars = max_tokens * 1.5 and overlap_chars = overlap_tokens *
            1.5, using the approximate rule 1 token ~= 1.5 Chinese characters.
            Overlap keeps boundary context: for example, with max_chars=100 and
            overlap_chars=15, chunk 1 covers [0:100] and chunk 2 starts at 85,
            so a sentence split near character 95 still appears with context in
            the next chunk.
        """
        clean_text = (text or "").strip()
        if not clean_text:
            return []

        max_chars = self._max_chars()
        overlap_chars = self._overlap_chars()
        step = max(1, max_chars - overlap_chars)

        chunks: List[ChunkModel] = []
        start = 0

        while start < len(clean_text):
            end = start + max_chars
            chunk_text = clean_text[start:end].strip()
            if chunk_text:
                chunks.append(self._make_chunk(
                    content=chunk_text,
                    chunk_index=len(chunks),
                    chunk_type="length",
                    heading_path=heading_path,
                    page_no=page_no,
                ))

            if end >= len(clean_text):
                break
            start += step

        return chunks

    def _table_to_chunk(self, table: dict, chunk_index: int) -> ChunkModel:
        """
        Convert a parsed table to a single ChunkModel.

        Args:
            table: Parsed table dict containing markdown_text and metadata.
            chunk_index: Temporary index before final ordering.

        Returns:
            ChunkModel: Table chunk.

        Notes:
            The whole table is kept as one chunk and is not split, because row
            or cell splitting can destroy the table's semantic relationships.
        """
        content = str(table.get("markdown_text") or "").strip()
        return self._make_chunk(
            content=content,
            chunk_index=chunk_index,
            chunk_type="table",
            heading_path=table.get("heading_path"),
            page_no=table.get("page_no"),
        )

    def _flush_heading_chunk(
        self,
        texts: List[str],
        heading_path: Optional[str],
        page_no: Optional[int],
        chunk_index: int,
    ) -> List[ChunkModel]:
        """
        Build one or more heading chunks from accumulated text.

        Args:
            texts: Accumulated block texts.
            heading_path: Heading path for the accumulated section.
            page_no: Starting page number for the section.
            chunk_index: Temporary index before final ordering.

        Returns:
            List[ChunkModel]: One heading chunk or length-split child chunks.
        """
        content = "\n".join(text for text in texts if text).strip()
        if not content:
            return []

        if len(content) > self._max_chars():
            split_chunks = self._chunk_by_length(content, heading_path, page_no)
            return self._reindex_chunks(split_chunks, chunk_index)

        return [self._make_chunk(
            content=content,
            chunk_index=chunk_index,
            chunk_type="heading",
            heading_path=heading_path,
            page_no=page_no,
        )]

    def _reindex_chunks(
        self,
        chunks: List[ChunkModel],
        start_index: int,
    ) -> List[ChunkModel]:
        """
        Rebuild chunks with contiguous temporary indexes.

        Args:
            chunks: Chunks to reindex.
            start_index: First index assigned to the rebuilt list.

        Returns:
            List[ChunkModel]: Chunks with indexes starting at start_index.
        """
        return [
            self._make_chunk(
                content=chunk.content,
                chunk_index=start_index + offset,
                chunk_type=chunk.chunk_type,
                heading_path=chunk.heading_path,
                page_no=chunk.page_no,
            )
            for offset, chunk in enumerate(chunks)
        ]

    def _make_chunk(
        self,
        content: str,
        chunk_index: int,
        chunk_type: str,
        heading_path: Optional[str] = None,
        page_no: Optional[int] = None,
    ) -> ChunkModel:
        """
        Create a ChunkModel with consistent char_count calculation.

        Args:
            content: Chunk text.
            chunk_index: Chunk order index.
            chunk_type: Chunking strategy type.
            heading_path: Optional heading path metadata.
            page_no: Optional source page number.

        Returns:
            ChunkModel: Validated chunk data model.
        """
        clean_content = (content or "").strip()
        return ChunkModel(
            content=clean_content,
            chunk_index=chunk_index,
            heading_path=heading_path,
            page_no=page_no,
            char_count=len(clean_content),
            chunk_type=chunk_type,
        )

    def _max_chars(self) -> int:
        """
        Return the maximum character count derived from max_tokens.

        Returns:
            int: Approximate maximum characters per chunk.
        """
        return max(1, int(self.max_tokens * self.TOKEN_TO_CHARS))

    def _overlap_chars(self) -> int:
        """
        Return the overlap character count derived from overlap_tokens.

        Returns:
            int: Approximate overlapping characters for sliding windows.
        """
        return max(0, int(self.overlap_tokens * self.TOKEN_TO_CHARS))

    @staticmethod
    def _get_text(block: dict) -> str:
        """
        Read and normalize text from a parser text block.

        Args:
            block: Parsed text block.

        Returns:
            str: Stripped block text.
        """
        return str(block.get("text") or "").strip()

    @staticmethod
    def _is_heading(block: dict) -> bool:
        """
        Read heading status from Word or PDF parser metadata.

        Args:
            block: Parsed text block.

        Returns:
            bool: True when the block is marked as a heading.
        """
        return bool(block.get("is_heading") or block.get("is_possible_heading"))

    def _join_block_texts(self, text_blocks: List[dict], separator: str) -> str:
        """
        Join non-empty text block contents with the given separator.

        Args:
            text_blocks: Parsed text blocks.
            separator: Separator inserted between block texts.

        Returns:
            str: Joined text.
        """
        return separator.join(
            text
            for text in (self._get_text(block) for block in text_blocks)
            if text
        )
