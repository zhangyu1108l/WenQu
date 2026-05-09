"""
Word document parser for Step 4-C.

Word parsing can read exact paragraph styles, so heading hierarchy is more
reliable than PDF heuristic heading detection.
"""

import logging
from io import BytesIO
from typing import List, Optional

from docx import Document
from docx.document import Document as DocumentObject
from docx.oxml.table import CT_Tbl
from docx.oxml.text.paragraph import CT_P
from docx.table import Table
from docx.text.paragraph import Paragraph

logger = logging.getLogger(__name__)


class WordParser:
    """
    Word document parser.

    The core advantage of Word parsing is that .docx keeps structured styles,
    especially Heading 1/2/3. This lets us build an accurate heading path for
    later source citation and chunk metadata.
    """

    def parse(self, file_bytes: bytes) -> dict:
        """
        Parse a Word document and extract structured text blocks plus tables.

        Args:
            file_bytes: Raw .docx bytes.

        Returns:
            dict: Contains text_blocks and tables.
        """
        logger.info("Start parsing Word document, size=%d bytes", len(file_bytes))

        doc = Document(BytesIO(file_bytes))
        text_blocks = self._extract_text_blocks(doc)
        tables = self._extract_tables(doc)

        logger.info(
            "Word parsing completed: text_blocks=%d, tables=%d",
            len(text_blocks),
            len(tables),
        )

        return {
            "text_blocks": text_blocks,
            "tables": tables,
        }

    def _extract_text_blocks(self, doc: DocumentObject) -> List[dict]:
        """
        Extract text blocks and preserve heading hierarchy.

        Heading style names must support both English and Chinese Word:
        English Word uses "Heading 1/2/3", while Chinese Word uses
        "标题 1/2/3".

        heading_stack keeps the current heading path. When a new heading is
        encountered, all deeper or same-level headings are replaced:
        H1 "第5章" -> stack=["第5章"]
        H2 "5.3节" -> stack=["第5章", "5.3节"]
        new H1 "第6章" -> stack=["第6章"] because H1 resets all lower levels.
        This stack maintenance is the core of heading path construction.
        """
        text_blocks: List[dict] = []
        heading_stack: List[str] = []

        for paragraph in doc.paragraphs:
            text = paragraph.text.strip()
            if not text:
                continue

            heading_level = self._get_heading_level(paragraph)
            is_heading = heading_level > 0

            if is_heading:
                self._update_heading_stack(heading_stack, heading_level, text)

            heading_path = self._build_heading_path(heading_stack)

            text_blocks.append({
                "text": text,
                "is_heading": is_heading,
                "heading_level": heading_level,
                "heading_path": heading_path,
                "char_count": len(text),
            })

        logger.info("Word text extraction completed: %d text blocks", len(text_blocks))
        return text_blocks

    def _build_heading_path(self, heading_stack: list) -> Optional[str]:
        """
        Build heading path from the current heading stack.

        The ">" separator follows the architecture doc example and is used by
        the frontend for readable source display. The result is stored later in
        doc_chunk.heading_path.
        """
        if not heading_stack:
            return None
        return ">".join(heading_stack)

    def _extract_tables(self, doc: DocumentObject) -> List[dict]:
        """
        Extract Word tables and convert them to Markdown.

        Word documents do not have a stable page_no concept like PDF files, so
        page_no is always None. Tables are attached to the nearest preceding
        heading path to provide context for retrieval and source citation.
        """
        tables: List[dict] = []
        heading_stack: List[str] = []

        for block in self._iter_block_items(doc):
            if isinstance(block, Paragraph):
                text = block.text.strip()
                if not text:
                    continue

                heading_level = self._get_heading_level(block)
                if heading_level > 0:
                    self._update_heading_stack(heading_stack, heading_level, text)
                continue

            if isinstance(block, Table):
                table_data = self._table_to_rows(block)
                if not table_data:
                    continue

                col_count = max((len(row) for row in table_data), default=0)
                if col_count == 0:
                    continue

                markdown_text = self._rows_to_markdown(table_data, col_count)
                if not markdown_text:
                    continue

                tables.append({
                    "markdown_text": markdown_text,
                    "row_count": len(table_data),
                    "col_count": col_count,
                    "page_no": None,
                    "heading_path": self._build_heading_path(heading_stack),
                })

        logger.info("Word table extraction completed: %d tables", len(tables))
        return tables

    @staticmethod
    def _get_heading_level(paragraph: Paragraph) -> int:
        style_name = paragraph.style.name if paragraph.style else ""

        if "Heading 1" in style_name or "标题 1" in style_name:
            return 1
        if "Heading 2" in style_name or "标题 2" in style_name:
            return 2
        if "Heading 3" in style_name or "标题 3" in style_name:
            return 3
        return 0

    @staticmethod
    def _update_heading_stack(heading_stack: List[str], level: int, text: str) -> None:
        del heading_stack[level - 1:]
        heading_stack.append(text)

    @staticmethod
    def _iter_block_items(doc: DocumentObject):
        for child in doc.element.body.iterchildren():
            if isinstance(child, CT_P):
                yield Paragraph(child, doc)
            elif isinstance(child, CT_Tbl):
                yield Table(child, doc)

    @staticmethod
    def _table_to_rows(table: Table) -> List[List[str]]:
        rows: List[List[str]] = []

        for row in table.rows:
            cells = [WordParser._clean_cell(cell.text) for cell in row.cells]
            if any(cells):
                rows.append(cells)

        return rows

    @staticmethod
    def _rows_to_markdown(rows: List[List[str]], col_count: int) -> str:
        normalized_rows: List[List[str]] = []

        for row in rows:
            normalized = list(row[:col_count])
            while len(normalized) < col_count:
                normalized.append("")
            normalized_rows.append(normalized)

        if not normalized_rows:
            return ""

        markdown_lines = [
            "| " + " | ".join(normalized_rows[0]) + " |",
            "| " + " | ".join(["---"] * col_count) + " |",
        ]

        for row in normalized_rows[1:]:
            markdown_lines.append("| " + " | ".join(row) + " |")

        return "\n".join(markdown_lines)

    @staticmethod
    def _clean_cell(cell_text: str) -> str:
        return str(cell_text or "").replace("\n", " ").strip()
