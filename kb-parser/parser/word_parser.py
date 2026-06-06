"""
Step 4-C 的 Word 文档解析器。

Word 解析可以直接读取段落样式，因此标题层级识别比 PDF 的启发式检测更可靠。
"""

import logging
from io import BytesIO
from typing import List, Optional
from zipfile import BadZipFile, is_zipfile

from docx import Document
from docx.document import Document as DocumentObject
from docx.oxml.table import CT_Tbl
from docx.oxml.text.paragraph import CT_P
from docx.table import Table
from docx.text.paragraph import Paragraph

logger = logging.getLogger(__name__)


class WordParseError(ValueError):
    """Word 文件格式错误或解析失败。"""


class WordParser:
    """
    Word 文档解析器。

    Word 解析的核心优势是 .docx 保留了结构化样式，
    尤其是 Heading 1/2/3 这类标题样式。借助这些样式，
    可以为后续来源引用和 chunk 元数据构建准确的标题路径。
    """

    def parse(self, file_bytes: bytes) -> dict:
        """
        解析 Word 文档，提取结构化文本块和表格。

        参数:
            file_bytes: 原始 .docx 文件字节数据。

        返回:
            dict: 包含 text_blocks 和 tables。
        """
        logger.info("开始解析 Word 文档，大小=%d 字节", len(file_bytes))

        file_buffer = BytesIO(file_bytes)
        if not is_zipfile(file_buffer):
            raise WordParseError("无效的 docx 文件，请上传真实的 .docx 文件")

        file_buffer.seek(0)
        try:
            doc = Document(file_buffer)
        except BadZipFile as exc:
            raise WordParseError("无效的 docx 文件，请上传真实的 .docx 文件") from exc
        except Exception as exc:
            raise WordParseError("docx 文件结构无效或已损坏") from exc

        text_blocks = self._extract_text_blocks(doc)
        tables = self._extract_tables(doc)

        logger.info(
            "Word 解析完成：文本块数量=%d，表格数量=%d",
            len(text_blocks),
            len(tables),
        )

        return {
            "text_blocks": text_blocks,
            "tables": tables,
        }

    def _extract_text_blocks(self, doc: DocumentObject) -> List[dict]:
        """
        提取文本块，并保留标题层级。

        标题样式名称需要同时兼容英文版和中文版 Word：
        英文版 Word 使用 "Heading 1/2/3"，中文版 Word 使用 "标题 1/2/3"。

        heading_stack 用来保存当前标题路径。遇到新标题时，
        会替换掉同级或更深层级的旧标题：
        H1 "第5章" -> stack=["第5章"]
        H2 "5.3节" -> stack=["第5章", "5.3节"]
        新 H1 "第6章" -> stack=["第6章"]，因为 H1 会重置所有下级标题。
        维护这个栈，是构建 heading_path 的核心逻辑。
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

        logger.info("Word 文本提取完成：文本块数量=%d", len(text_blocks))
        return text_blocks

    def _build_heading_path(self, heading_stack: list) -> Optional[str]:
        """
        根据当前标题栈构建标题路径。

        ">" 分隔符遵循架构文档中的示例，前端会用它展示可读的来源路径。
        构建结果后续会存入 doc_chunk.heading_path。
        """
        if not heading_stack:
            return None
        return ">".join(heading_stack)

    def _extract_tables(self, doc: DocumentObject) -> List[dict]:
        """
        提取 Word 表格，并转换为 Markdown。

        Word 文档不像 PDF 那样有稳定的 page_no 概念，所以 page_no 固定为 None。
        表格会挂到最近的前置标题路径下，为检索和来源引用提供上下文。
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

        logger.info("Word 表格提取完成：表格数量=%d", len(tables))
        return tables

    @staticmethod
    def _get_heading_level(paragraph: Paragraph) -> int:
        try:
            style = paragraph.style
            style_name = str(style.name or "") if style else ""
        except Exception:
            logger.exception("读取 Word 段落样式失败，按普通段落处理")
            return 0

        normalized_style_name = style_name.replace(" ", "").lower()

        if "heading1" in normalized_style_name or "标题1" in normalized_style_name:
            return 1
        if "heading2" in normalized_style_name or "标题2" in normalized_style_name:
            return 2
        if "heading3" in normalized_style_name or "标题3" in normalized_style_name:
            return 3
        return 0

    @staticmethod
    def _update_heading_stack(heading_stack: List[str], level: int, text: str) -> None:
        clean_text = str(text or "").strip()
        if level <= 0 or not clean_text:
            return
        del heading_stack[level - 1:]
        heading_stack.append(clean_text)

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
