"""
Step 4-D 的智能文档分块器。

三级降级决策流程：

    有标题？ -> 是 -> 按标题分块
          |
          否
          ↓
    有段落？ -> 是 -> 按段落分块
          |
          否
          ↓
    长度兜底
"""

from typing import List, Optional

from models.schema import ChunkModel


# 三级降级决策流程：
#   有标题？ -> 是 -> 标题分块
#       |
#       否
#       ↓
#   有段落？ -> 是 -> 段落分块
#       |
#       否
#       ↓
#   长度兜底
class SmartChunker:
    """
    采用三级降级策略的智能分块器。

    该策略优先使用语义边界；只有更强的结构不可用时，才降级到较弱结构：
    标题分块可以保留最完整的文档语义；段落分块是次优的自然阅读单元；
    长度分块则用于保证无结构文本也能被安全地索引和检索。
    """

    TOKEN_TO_CHARS = 1.5

    def __init__(
        self,
        max_tokens: int = 512,
        overlap_tokens: int = 50,
        min_chunk_chars: int = 50,
    ) -> None:
        """
        初始化分块参数。

        参数:
            max_tokens: 单个 chunk 的近似最大 token 数。
            overlap_tokens: 长度兜底分块时，相邻 chunk 的近似重叠 token 数。
            min_chunk_chars: 分块后保留 chunk 的最小字符数。

        说明:
            这里用字符数近似 token 数，因为精确计算 token 需要额外的 tokenizer 依赖。
            对中文文档来说，1 token 约等于 1.5 个中文字符的估算已经足够用于控制
            chunk 大小，同时可以让解析侧车保持轻量。
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
        将解析出的文本块和表格分块为 ChunkModel 对象。

        参数:
            text_blocks: PDF 或 Word 解析器产出的结构化文本块。
            tables: PDF 或 Word 解析器产出的结构化表格块。
            file_type: 源文件类型，例如 "pdf" 或 "docx"。

        返回:
            List[ChunkModel]: 有序 chunk 列表，chunk_index 会从 0 重新编号。

        说明:
            降级优先级为：标题 -> 段落 -> 长度。标题分块保留最丰富的语义结构；
            没有标题时，段落是自然的阅读单元；对于没有明显结构的纯文本，
            长度兜底可以保护检索质量。表格始终在文本分块后作为完整 chunk 合并，
            因为表格是独立语义单元，不参与文本降级策略判断。
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
        判断文档是否存在可用的标题结构。

        参数:
            text_blocks: 已解析的文本块。

        返回:
            bool: 当至少存在两个类似标题的文本块时返回 True。

        说明:
            阈值设为 2，是因为单个标题可能只是文档标题。
            至少两个标题才能说明文档有真实的层级结构。
        """
        heading_count = sum(1 for block in text_blocks if self._is_heading(block))
        return heading_count >= 2

    def _has_paragraph_structure(
        self,
        text_blocks: List[dict],
        file_type: str = "",
    ) -> bool:
        """
        判断文档是否存在段落分隔结构。

        参数:
            text_blocks: 已解析的文本块。

        返回:
            bool: 当存在段落边界时返回 True。

        说明:
            双换行是抽取文本中常见的段落分隔符，因此出现双换行通常表示存在段落结构。
            Word 解析本身已经按段落产出文本块，所以多个 Word 文本块也会被视为段落结构。
        """
        text = self._join_block_texts(text_blocks, separator="\n")
        if "\n\n" in text:
            return True

        return file_type == "docx" and sum(
            1 for block in text_blocks if self._get_text(block)
        ) >= 2

    def _chunk_by_heading(self, text_blocks: List[dict]) -> List[ChunkModel]:
        """
        按标题边界对文本进行分块。

        参数:
            text_blocks: 带有标题元数据的已解析文本块。

        返回:
            List[ChunkModel]: 以标题作为起点边界的 chunk 列表。

        说明:
            标题文本本身会被放入 chunk 内容，否则 chunk 会丢失最重要的局部上下文。
            如果某个标题段落下的内容太长，会继续按长度递归拆分，
            因为有些章节内容会明显超过向量化和索引的目标大小。
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
        按段落分隔符对文本进行分块。

        参数:
            text_blocks: 已解析的文本块。

        返回:
            List[ChunkModel]: 基于段落生成的 chunk 列表。

        说明:
            短段落会和后一个段落合并，以避免产生过碎的 chunk。
            过小的 chunk 往往缺少足够上下文，难以和用户问题形成良好匹配，
            从而影响检索质量。
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
        使用滑动窗口按长度对文本进行分块。

        参数:
            text: 待切分的原始文本。
            heading_path: 可选标题路径，会复制到生成的 chunk 中。
            page_no: 可选页码，会复制到生成的 chunk 中。

        返回:
            List[ChunkModel]: 基于长度生成的 chunk 列表。

        说明:
            max_chars = max_tokens * 1.5，overlap_chars = overlap_tokens * 1.5，
            使用的是 1 token 约等于 1.5 个中文字符的估算规则。
            重叠窗口用于保留边界上下文：例如 max_chars=100、overlap_chars=15 时，
            chunk 1 覆盖 [0:100]，chunk 2 从 85 开始，
            因此在第 95 个字符附近被切开的句子，仍会带着上下文出现在下一个 chunk 中。
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
        将已解析的表格转换为单个 ChunkModel。

        参数:
            table: 已解析的表格字典，包含 markdown_text 和元数据。
            chunk_index: 最终排序前使用的临时索引。

        返回:
            ChunkModel: 表格 chunk。

        说明:
            整张表格会作为一个 chunk 保留，不会继续拆分，
            因为按行或单元格拆分可能破坏表格内部的语义关系。
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
        根据已累积的文本构建一个或多个标题 chunk。

        参数:
            texts: 已累积的文本块内容。
            heading_path: 当前累积章节的标题路径。
            page_no: 当前章节的起始页码。
            chunk_index: 最终排序前使用的临时索引。

        返回:
            List[ChunkModel]: 一个标题 chunk，或按长度拆出的多个子 chunk。
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
        使用连续的临时索引重建 chunk 列表。

        参数:
            chunks: 需要重新编号的 chunk。
            start_index: 重建后列表分配的第一个索引。

        返回:
            List[ChunkModel]: 索引从 start_index 开始的 chunk 列表。
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
        创建 ChunkModel，并统一计算 char_count。

        参数:
            content: chunk 文本内容。
            chunk_index: chunk 顺序索引。
            chunk_type: 分块策略类型。
            heading_path: 可选标题路径元数据。
            page_no: 可选来源页码。

        返回:
            ChunkModel: 通过校验的 chunk 数据模型。
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
        返回由 max_tokens 换算出的最大字符数。

        返回:
            int: 每个 chunk 的近似最大字符数。
        """
        return max(1, int(self.max_tokens * self.TOKEN_TO_CHARS))

    def _overlap_chars(self) -> int:
        """
        返回由 overlap_tokens 换算出的重叠字符数。

        返回:
            int: 滑动窗口使用的近似重叠字符数。
        """
        return max(0, int(self.overlap_tokens * self.TOKEN_TO_CHARS))

    @staticmethod
    def _get_text(block: dict) -> str:
        """
        从解析器文本块中读取并规范化文本。

        参数:
            block: 已解析的文本块。

        返回:
            str: 去除首尾空白后的文本块内容。
        """
        return str(block.get("text") or "").strip()

    @staticmethod
    def _is_heading(block: dict) -> bool:
        """
        从 Word 或 PDF 解析器元数据中读取标题状态。

        参数:
            block: 已解析的文本块。

        返回:
            bool: 当该文本块被标记为标题时返回 True。
        """
        return bool(block.get("is_heading") or block.get("is_possible_heading"))

    def _join_block_texts(self, text_blocks: List[dict], separator: str) -> str:
        """
        使用指定分隔符拼接非空文本块内容。

        参数:
            text_blocks: 已解析的文本块。
            separator: 插入到文本块之间的分隔符。

        返回:
            str: 拼接后的文本。
        """
        return separator.join(
            text
            for text in (self._get_text(block) for block in text_blocks)
            if text
        )
