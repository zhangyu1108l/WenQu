package com.kb.app.rag.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.kb.app.rag.dto.ChunkDTO;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;

/**
 * Python 文档解析侧车 HTTP 客户端。
 * <p>
 * 调用 Python FastAPI 侧车的 POST /parse 接口，发送文件字节流，
 * 接收解析后的 chunk 列表（文本分块结果）。
 * <p>
 * <b>请求格式：</b>multipart/form-data，包含两个字段：
 * <ul>
 *     <li>file — 文件字节流</li>
 *     <li>file_type — 文件类型（pdf / docx）</li>
 * </ul>
 * <p>
 * <b>降级处理说明：</b>
 * 如果 Python 侧车不可用（连接超时、500 错误等），本方法会抛出 RuntimeException，
 * 由上层调用方（DocUploadServiceImpl.processAsync）捕获异常后：
 * <ul>
 *     <li>将 async_task 状态标记为 FAILED</li>
 *     <li>将 document 状态标记为 FAILED</li>
 *     <li>记录错误日志，便于运维排查侧车服务状态</li>
 * </ul>
 * 当前版本不做自动重试，后续可根据需要引入重试机制。
 *
 * @author kb-system
 */
@Slf4j
@Component
public class ParserClient {

    /**
     * Python 解析侧车基础地址，从配置文件读取。
     * 开发环境：http://localhost:8090
     * Docker 环境：http://parser:8090
     */
    @Value("${sidecar.parser-url}")
    private String parserBaseUrl;

    private final RestTemplate restTemplate;

    public ParserClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * 调用 Python 侧车解析文档，返回 chunk 列表。
     * <p>
     * 使用 multipart/form-data 格式发送文件字节和文件类型，
     * Python 侧车根据 file_type 选择对应的解析策略（PDF / Word）。
     * <p>
     * <b>异常场景：</b>
     * <ul>
     *     <li>侧车未启动 → RestClientException（Connection refused）</li>
     *     <li>侧车解析失败 → HTTP 500 + 错误信息</li>
     *     <li>文件格式不支持 → HTTP 400</li>
     * </ul>
     * 以上异常均包装为 RuntimeException 向上抛出，由异步任务统一处理。
     *
     * @param fileBytes 文件字节数组
     * @param fileType  文件类型（pdf / docx）
     * @return 解析后的 chunk 列表
     * @throws RuntimeException 侧车不可用或解析失败时抛出
     */
    public List<ChunkDTO> parse(byte[] fileBytes, String fileType) {
        String url = parserBaseUrl + "/parse";
        log.info("调用 Python 解析侧车: url={}, fileType={}, fileSize={} bytes",
                url, fileType, fileBytes.length);

        try {
            // 构建 multipart/form-data 请求体
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();

            // 文件字段：使用 ByteArrayResource 包装字节数组，并重写 getFilename()
            // 使 RestTemplate 能正确发送 multipart 文件
            ByteArrayResource fileResource = new ByteArrayResource(fileBytes) {
                @Override
                public String getFilename() {
                    return "file." + fileType;
                }
            };
            body.add("file", fileResource);
            body.add("file_type", fileType);

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            // 发送请求并解析响应
            ResponseEntity<ParseResponse> response = restTemplate.postForEntity(
                    url, requestEntity, ParseResponse.class);

            if (response.getBody() == null || response.getBody().getChunks() == null) {
                log.warn("Python 侧车返回空结果: url={}, fileType={}", url, fileType);
                return Collections.emptyList();
            }

            List<ChunkDTO> chunks = response.getBody().getChunks();
            log.info("Python 解析侧车返回: chunkCount={}", chunks.size());
            return chunks;

        } catch (RestClientException e) {
            // 侧车连接失败、超时、HTTP 错误等
            log.error("调用 Python 解析侧车失败: url={}, fileType={}, error={}",
                    url, fileType, e.getMessage());
            throw new RuntimeException("文档解析失败：Python 侧车不可用 (" + e.getMessage() + ")", e);
        }
    }

    /**
     * Python 侧车响应体内部类。
     * <p>
     * 对应 Python 接口返回格式：{"chunks": [...]}
     */
    @Data
    private static class ParseResponse {
        private List<ChunkDTO> chunks;
    }
}
