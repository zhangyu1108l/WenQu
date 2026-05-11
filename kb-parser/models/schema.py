"""
kb-parser 数据模型定义模块

定义文档解析接口的请求和响应数据结构，
与 Java 主服务 ParserClient 的 ChunkDTO 对应。
"""

from typing import Optional
from pydantic import BaseModel, Field


class ChunkModel(BaseModel):
    """单个文档分块的数据结构，对应解析后的一个文本片段"""

    content: str = Field(
        ...,
        description="原始文本内容，用于向量化和来源引用展示"
    )
    chunk_index: int = Field(
        ...,
        ge=0,
        description="chunk 在文档中的顺序索引，从 0 开始递增"
    )
    heading_path: Optional[str] = Field(
        default=None,
        description="标题路径，格式如'第5章>5.3节>年假规定'，无标题结构则为 None"
    )
    page_no: Optional[int] = Field(
        default=None,
        ge=1,
        description="PDF 文档的页码（从1开始），Word 文档固定为 None"
    )
    char_count: int = Field(
        ...,
        ge=0,
        description="该 chunk 的字符数量（len(content)）"
    )
    chunk_type: str = Field(
        ...,
        description=(
            "分块类型，取值范围："
            "heading - 基于标题层级切分（Word标题样式/PDF书签）；"
            "paragraph - 基于双换行符的段落切分；"
            "length - 长度兜底切分（512 token，50 token 重叠）；"
            "table - 整张表格一个 chunk（Markdown），不拆分"
        )
    )


class ParseResponse(BaseModel):
    """文档解析接口的响应体，包含所有 chunk 和解析元信息"""

    chunks: list[ChunkModel] = Field(
        ...,
        description="解析后的 chunk 列表，按 chunk_index 升序排列"
    )
    total_chunks: int = Field(
        ...,
        ge=0,
        description="chunk 总数，等于 len(chunks)"
    )
    file_type: str = Field(
        ...,
        description="文件类型标识：pdf 或 docx"
    )
    parse_time_ms: int = Field(
        ...,
        ge=0,
        description="解析总耗时，单位毫秒"
    )


class ParseRequest(BaseModel):
    """
    解析接口请求参数说明（仅用于接口文档描述）。
    实际接口以 multipart/form-data 形式接收：
      - file: 上传的文件二进制
      - file_type: 文件类型字符串
    """

    file_type: str = Field(
        ...,
        description="文件类型，取值：pdf 或 docx"
    )
