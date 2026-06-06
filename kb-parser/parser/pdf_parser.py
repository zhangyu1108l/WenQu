"""
PDF 文档解析器模块

职责：从 PDF 文件中提取结构化文本内容和表格数据。
两种解析路径：
  1. 文字版 PDF：直接用 pdfplumber 提取文字，速度快、精度高
  2. 扫描版 PDF：先将页面渲染为图片，再用 pytesseract OCR 识别文字

判断逻辑：取前几页提取文字，平均字符数极少则判定为扫描版。
"""

import logging
import os
import shutil
from pathlib import Path
from typing import List

import pdfplumber
import pytesseract
from pytesseract import TesseractError, TesseractNotFoundError
from io import BytesIO

logger = logging.getLogger(__name__)

REQUIRED_OCR_LANGUAGES = {"chi_sim", "eng"}
PROJECT_TESSDATA_DIR = Path(__file__).resolve().parents[1] / "tessdata"


class OcrDependencyError(RuntimeError):
    """Raised when OCR is requested but the Tesseract runtime is unavailable."""


class OcrExecutionError(RuntimeError):
    """Raised when Tesseract is installed but cannot complete OCR."""


def _configure_tesseract_cmd() -> None:
    """Allow local Windows dev to run OCR without requiring PATH changes."""
    configured_cmd = os.getenv("TESSERACT_CMD") or os.getenv("TESSERACT_PATH")
    candidates = []
    if configured_cmd:
        candidates.append(configured_cmd)

    if os.name == "nt":
        candidates.extend([
            r"C:\Program Files\Tesseract-OCR\tesseract.exe",
            r"C:\Program Files (x86)\Tesseract-OCR\tesseract.exe",
        ])

    for candidate in candidates:
        candidate_path = Path(candidate)
        if candidate_path.exists():
            pytesseract.pytesseract.tesseract_cmd = str(candidate_path)
            return

    found_cmd = shutil.which("tesseract")
    if found_cmd:
        pytesseract.pytesseract.tesseract_cmd = found_cmd


_configure_tesseract_cmd()


def _candidate_tessdata_dirs() -> List[Path]:
    candidates = []

    configured_dir = os.getenv("TESSDATA_DIR")
    if configured_dir:
        candidates.append(Path(configured_dir))

    tessdata_prefix = os.getenv("TESSDATA_PREFIX")
    if tessdata_prefix:
        prefix_path = Path(tessdata_prefix)
        candidates.append(prefix_path)
        candidates.append(prefix_path / "tessdata")

    candidates.append(PROJECT_TESSDATA_DIR)

    if os.name == "nt":
        candidates.extend([
            Path(r"C:\Program Files\Tesseract-OCR\tessdata"),
            Path(r"C:\Program Files (x86)\Tesseract-OCR\tessdata"),
        ])

    return candidates


def _resolve_tessdata_dir() -> Path | None:
    existing_dirs = []
    for candidate in _candidate_tessdata_dirs():
        if candidate.exists() and candidate.is_dir():
            existing_dirs.append(candidate)
            if all((candidate / f"{lang}.traineddata").exists() for lang in REQUIRED_OCR_LANGUAGES):
                return candidate

    return existing_dirs[0] if existing_dirs else None


def _tessdata_config() -> str:
    tessdata_dir = _resolve_tessdata_dir()
    if not tessdata_dir:
        return ""
    return f'--tessdata-dir "{tessdata_dir}"'


def get_ocr_status() -> dict:
    tessdata_dir = _resolve_tessdata_dir()
    tessdata_config = _tessdata_config()
    try:
        version = str(pytesseract.get_tesseract_version())
        languages = set(pytesseract.get_languages(config=tessdata_config))
    except TesseractNotFoundError:
        return {
            "available": False,
            "tessdata_dir": str(tessdata_dir) if tessdata_dir else None,
            "error": "Tesseract executable is unavailable.",
        }
    except TesseractError:
        return {
            "available": False,
            "tessdata_dir": str(tessdata_dir) if tessdata_dir else None,
            "error": "Tesseract language data cannot be read.",
        }

    missing_languages = sorted(REQUIRED_OCR_LANGUAGES - languages)
    if missing_languages:
        return {
            "available": False,
            "version": version,
            "tessdata_dir": str(tessdata_dir) if tessdata_dir else None,
            "missing_languages": missing_languages,
            "error": "Tesseract is missing required OCR language data.",
        }

    return {
        "available": True,
        "version": version,
        "tessdata_dir": str(tessdata_dir) if tessdata_dir else None,
    }


class PdfParser:
    """
    PDF 文档解析器

    根据 PDF 内容自动判断是文字版还是扫描版，选择对应的解析路径：
    - 文字版：pdfplumber 直接提取文字 + 表格
    - 扫描版：pytesseract OCR 识别文字 + pdfplumber 提取表格

    注意：本类只负责解析和提取，不做分块逻辑。
    """

    # 扫描版判定阈值：前几页平均字符数低于此值则认为是扫描版
    # pdfplumber 对扫描版 PDF 几乎提取不到文字，通常每页只有零星几个字符
    SCAN_THRESHOLD = 100

    # 扫描版检测时取样的最大页数，避免大文件（数百页）全部检测导致处理缓慢
    SCAN_CHECK_MAX_PAGES = 3

    # OCR 渲染分辨率（DPI），200 是速度和识别率的平衡点：
    # - 150 以下：小号字体识别率明显下降
    # - 300 以上：处理时间翻倍但识别率提升有限
    OCR_RESOLUTION = 200

    # OCR 置信度过滤阈值（0~100），低于此值的识别结果视为噪声丢弃
    # 60 是经验值：能过滤大部分误识别，同时保留正常文字
    OCR_CONFIDENCE_THRESHOLD = 60

    # 标题启发式判定：字符数上限，短文本且不以标点结尾可能是标题
    HEADING_MAX_CHARS = 30

    # 中英文常见标点符号集合，用于标题启发式判定
    PUNCTUATION_ENDINGS = ('。', '；', '，', '、', '！', '？', '.', ';', ',', '!', '?', '…')

    def parse(self, file_bytes: bytes) -> dict:
        """
        PDF 解析主入口方法

        根据 PDF 内容自动判断解析路径（文字版/扫描版），提取文本块和表格。

        参数:
            file_bytes: PDF 文件的原始字节内容

        返回:
            dict: 包含以下字段：
                - text_blocks (List[dict]): 提取的文本块列表，每项含 text/page_no/is_possible_heading
                - tables (List[dict]): 提取的表格列表，每项含 markdown_text/page_no/row_count/col_count
                - is_scanned (bool): 是否是扫描版 PDF
        """
        logger.info("开始解析 PDF 文件，大小: %d 字节", len(file_bytes))

        with pdfplumber.open(BytesIO(file_bytes)) as pdf:
            is_scanned = self._check_is_scanned(pdf)
            logger.info("PDF 类型判定: %s", "扫描版" if is_scanned else "文字版")

            if is_scanned:
                # 扫描版走 OCR 路径
                text_blocks = self._extract_text_ocr(pdf)
            else:
                # 文字版直接提取文字
                text_blocks = self._extract_text_blocks(pdf)

            # 表格提取：无论文字版还是扫描版，pdfplumber 都能识别表格线条
            tables = self._extract_tables(pdf)

        logger.info(
            "PDF 解析完成: text_blocks=%d, tables=%d, is_scanned=%s",
            len(text_blocks), len(tables), is_scanned
        )

        return {
            "text_blocks": text_blocks,
            "tables": tables,
            "is_scanned": is_scanned,
        }

    def _check_is_scanned(self, pdf: pdfplumber.PDF) -> bool:
        """
        判断 PDF 是否是扫描版

        判定逻辑：
          1. 取前 N 页（最多 SCAN_CHECK_MAX_PAGES 页，避免大文件处理慢）
          2. 用 pdfplumber 提取每页文字，对非空页累计字符数后除以页数，得到平均每页字符数
          3. 平均每页字符数 < SCAN_THRESHOLD（100）则判定为扫描版
             扫描版 PDF 的文字层几乎为空，pdfplumber 提取到的字符极少（通常远低于 100）

        参数:
            pdf: 已打开的 pdfplumber.PDF 对象

        返回:
            bool: True 表示扫描版，False 表示文字版
        """
        # 只取前几页检测，避免大文件（如500页）全部扫描导致判定耗时过长
        check_pages = min(len(pdf.pages), self.SCAN_CHECK_MAX_PAGES)

        if check_pages == 0:
            # 空 PDF 按文字版处理（无页面无需 OCR）
            return False

        total_chars = 0
        for i in range(check_pages):
            page = pdf.pages[i]
            text = page.extract_text() or ""
            total_chars += len(text.strip())

        avg_chars = total_chars / check_pages

        # 阈值 SCAN_THRESHOLD（100）与架构约定一致：正常文字版每页通常数百字符，
        # 扫描版用 pdfplumber 提取往往极少（页眉页码等零星文字层）。
        logger.debug("扫描版检测: 前%d页平均字符数=%.1f, 阈值=%d", check_pages, avg_chars, self.SCAN_THRESHOLD)
        return avg_chars < self.SCAN_THRESHOLD

    def _extract_text_blocks(self, pdf: pdfplumber.PDF) -> List[dict]:
        """
        提取文字版 PDF 的文本块

        逻辑：
          1. 遍历每一页，用 pdfplumber 提取文字
          2. 按换行符分割为多个文本行
          3. 过滤空行和纯空白行
          4. 启发式识别可能的标题行（短文本 + 不以标点结尾）

        注意：标题识别是启发式规则，不是100%准确。
        最终由下游的 smart_chunker 分块器根据上下文综合决定分块边界。

        参数:
            pdf: 已打开的 pdfplumber.PDF 对象

        返回:
            List[dict]: 文本块列表，每项包含：
                - text (str): 文本内容
                - page_no (int): 所在页码（从1开始）
                - is_possible_heading (bool): 是否可能是标题
        """
        text_blocks: List[dict] = []

        for page_idx, page in enumerate(pdf.pages):
            page_no = page_idx + 1  # 页码从1开始，符合人类阅读习惯
            raw_text = page.extract_text() or ""

            # 按换行符分割，每行作为一个候选文本块
            lines = raw_text.split("\n")

            for line in lines:
                stripped = line.strip()

                # 跳过空行和纯空白行
                if not stripped:
                    continue

                # 启发式标题判定规则：
                # - 字符数少于阈值（标题通常较短）
                # - 不以常见标点结尾（正文段落通常以句号等结尾，标题则不会）
                # 这只是辅助信息，最终分块决策由 smart_chunker 负责
                is_possible_heading = (
                    len(stripped) < self.HEADING_MAX_CHARS
                    and not stripped.endswith(self.PUNCTUATION_ENDINGS)
                )

                text_blocks.append({
                    "text": stripped,
                    "page_no": page_no,
                    "is_possible_heading": is_possible_heading,
                })

        logger.info("文字版提取完成: 共 %d 个文本块", len(text_blocks))
        return text_blocks

    def _extract_text_ocr(self, pdf: pdfplumber.PDF) -> List[dict]:
        """
        OCR 扫描版 PDF 的文本提取

        逻辑：
          1. 遍历每一页，将页面渲染为图片
          2. 调用 pytesseract OCR 识别文字（中英文混合：chi_sim+eng）
          3. 过滤低置信度结果（conf < 60 视为噪声）
          4. 将同一行（相同 block_num + line_num）的文字合并为一个文本块

        注意：
          - resolution=200 是速度与识别率的平衡点（太低识别率差，太高处理慢）
          - 置信度范围 0~100，越高越准确，60 以下通常是误识别或图片噪点
          - OCR 结果不做标题识别，因为 OCR 文字无字体大小等格式信息，无法启发式判断

        参数:
            pdf: 已打开的 pdfplumber.PDF 对象

        返回:
            List[dict]: 文本块列表，每项包含：
                - text (str): OCR 识别的文本内容
                - page_no (int): 所在页码（从1开始）
                - is_possible_heading (bool): 固定为 False（OCR 无格式信息）
        """
        text_blocks: List[dict] = []

        for page_idx, page in enumerate(pdf.pages):
            page_no = page_idx + 1
            logger.debug("OCR 处理第 %d 页", page_no)

            # 将页面渲染为图片，resolution 控制 DPI
            # 200 DPI 是经验最佳值：150 以下小字识别率骤降，300 以上耗时翻倍收益甚微
            page_image = page.to_image(resolution=self.OCR_RESOLUTION)
            pil_image = page_image.original

            # 使用 pytesseract 获取详细识别数据（含置信度和行列信息）
            # lang="chi_sim+eng" 支持中英文混合文档
            try:
                ocr_data = pytesseract.image_to_data(
                    pil_image,
                    lang="chi_sim+eng",
                    config=_tessdata_config(),
                    output_type=pytesseract.Output.DICT,
                )
            except TesseractNotFoundError as exc:
                raise OcrDependencyError(
                    "OCR runtime is unavailable: install Tesseract OCR and the chi_sim/eng language packs, "
                    "or set TESSERACT_CMD to tesseract.exe."
                ) from exc
            except TesseractError as exc:
                raise OcrExecutionError(
                    "OCR execution failed: verify Tesseract has chi_sim and eng language data installed."
                ) from exc

            # 按行合并 OCR 识别结果
            # ocr_data 中每个元素是一个"词"，通过 block_num + line_num 确定所属行
            current_line_key = None
            current_line_words: List[str] = []

            num_items = len(ocr_data["text"])
            for i in range(num_items):
                conf = ocr_data["conf"][i]
                text = ocr_data["text"][i]

                # 置信度过滤：conf 为 -1 表示非文字区域，< 阈值表示识别不可靠
                # 低置信度结果通常是图片噪点、水印残影等，保留会引入垃圾文字
                try:
                    if float(conf) < self.OCR_CONFIDENCE_THRESHOLD:
                        continue
                except (TypeError, ValueError):
                    continue

                word = text.strip()
                if not word:
                    continue

                # 用 block_num + line_num 组合作为行标识，同一行的词合并
                line_key = (ocr_data["block_num"][i], ocr_data["line_num"][i])

                if line_key != current_line_key:
                    # 遇到新行，先保存上一行
                    if current_line_key is not None and current_line_words:
                        merged_text = " ".join(current_line_words).strip()
                        if merged_text:
                            text_blocks.append({
                                "text": merged_text,
                                "page_no": page_no,
                                # OCR 文字无字体大小/加粗等格式信息，无法判断是否为标题
                                "is_possible_heading": False,
                            })
                    current_line_key = line_key
                    current_line_words = [word]
                else:
                    current_line_words.append(word)

            # 处理最后一行
            if current_line_words:
                merged_text = " ".join(current_line_words).strip()
                if merged_text:
                    text_blocks.append({
                        "text": merged_text,
                        "page_no": page_no,
                        "is_possible_heading": False,
                    })

        logger.info("OCR 提取完成: 共 %d 个文本块", len(text_blocks))
        return text_blocks

    def _extract_tables(self, pdf: pdfplumber.PDF) -> List[dict]:
        """
        提取 PDF 中的表格并转为 Markdown 格式

        逻辑：
          1. 遍历每一页，调用 pdfplumber 的 extract_tables() 获取表格数据
          2. 将每个表格转为 Markdown 格式（表头 + 分隔行 + 数据行）
          3. 整张表格作为单个文本块保留

        设计决策：
          - 整张表格转为单个 chunk，不按行拆分。因为表格行拆开后语义丢失，
            例如"营收 | 同比增长 | 30%"拆开后"30%"毫无意义。
          - 使用 Markdown 格式是因为 LLM 能很好地理解 Markdown 表格结构，
            在回答时可以正确引用表格内容。

        参数:
            pdf: 已打开的 pdfplumber.PDF 对象

        返回:
            List[dict]: 表格列表，每项包含：
                - markdown_text (str): Markdown 格式的表格文本
                - page_no (int): 表格所在页码（从1开始）
                - row_count (int): 表格行数（含表头）
                - col_count (int): 表格列数
        """
        tables: List[dict] = []

        for page_idx, page in enumerate(pdf.pages):
            page_no = page_idx + 1
            page_tables = page.extract_tables() or []

            for table in page_tables:
                if not table or len(table) < 2:
                    # 少于2行的表格（只有表头没有数据）跳过，无实际意义
                    continue

                row_count = len(table)
                col_count = len(table[0]) if table[0] else 0

                if col_count == 0:
                    continue

                # 转换为 Markdown 表格格式
                markdown_lines: List[str] = []

                # 第一行作为表头
                header_cells = [self._clean_cell(cell) for cell in table[0]]
                markdown_lines.append("| " + " | ".join(header_cells) + " |")

                # 分隔行：Markdown 表格语法要求表头下有 |---|---| 分隔
                markdown_lines.append("| " + " | ".join(["---"] * col_count) + " |")

                # 数据行
                for row in table[1:]:
                    row_cells = [self._clean_cell(cell) for cell in row]
                    # 处理列数不一致的情况（合并单元格可能导致列数不同）
                    while len(row_cells) < col_count:
                        row_cells.append("")
                    markdown_lines.append("| " + " | ".join(row_cells[:col_count]) + " |")

                markdown_text = "\n".join(markdown_lines)

                tables.append({
                    "markdown_text": markdown_text,
                    "page_no": page_no,
                    "row_count": row_count,
                    "col_count": col_count,
                })

        logger.info("表格提取完成: 共 %d 个表格", len(tables))
        return tables

    @staticmethod
    def _clean_cell(cell) -> str:
        """
        清理表格单元格内容

        处理 pdfplumber 提取的原始单元格值：None 转空字符串，换行符替换为空格。

        参数:
            cell: pdfplumber 提取的单元格原始值，可能为 None 或含换行符的字符串

        返回:
            str: 清理后的单元格文本
        """
        if cell is None:
            return ""
        # 单元格内可能有换行（多行文字挤在一个格子里），替换为空格保持单行
        return str(cell).replace("\n", " ").strip()
