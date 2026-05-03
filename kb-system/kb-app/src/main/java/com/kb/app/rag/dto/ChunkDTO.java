package com.kb.app.rag.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文档分块 DTO — 对应 Python 解析侧车返回的 chunk 格式。
 * <p>
 * Python 侧车 POST /parse 接口返回示例：
 * <pre>
 * {"chunks": [{
 *     "content":      "文本内容",
 *     "chunk_index":  0,
 *     "heading_path": "第5章>5.3节",
 *     "page_no":      12,
 *     "char_count":   248,
 *     "chunk_type":   "heading"
 * }]}
 * </pre>
 * <p>
 * Python 侧使用 snake_case 命名，Java 侧使用 camelCase，
 * 通过 {@link JsonProperty} 注解完成自动映射。
 *
 * @author kb-system
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChunkDTO {

    /** 分块文本内容 */
    private String content;

    /** 分块在文档中的顺序索引（从 0 开始） */
    @JsonProperty("chunk_index")
    private Integer chunkIndex;

    /**
     * 标题路径，如"第5章>5.3节>年假规定"。
     * 无标题结构的文档此字段为 null。
     */
    @JsonProperty("heading_path")
    private String headingPath;

    /**
     * PDF 页码（从 1 开始）。
     * Word 文档此字段为 null。
     */
    @JsonProperty("page_no")
    private Integer pageNo;

    /** 分块字符数 */
    @JsonProperty("char_count")
    private Integer charCount;

    /**
     * 分块策略类型：
     * <ul>
     *     <li>heading — 按标题结构切分</li>
     *     <li>paragraph — 按段落（双换行符）切分</li>
     *     <li>length — 长度兜底切分（512 token，50 token 重叠）</li>
     * </ul>
     */
    @JsonProperty("chunk_type")
    private String chunkType;
}
