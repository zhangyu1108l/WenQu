package com.kb.app.rag.client;

import com.kb.app.rag.dto.ChunkDTO;
import com.kb.common.exception.BusinessException;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;

/**
 * Python 文档解析侧车 HTTP 客户端。
 * <p>
 * 负责调用 kb-parser 的 /parse 和 /health 接口，将 Java 上传文件转换为
 * Python 侧车可识别的 multipart/form-data 请求。
 */
@Slf4j
@Component
public class ParserClient {

    /**
     * Python 解析侧车基础地址。
     * <p>
     * 开发环境通常为 http://localhost:8090，Docker 网络内为 http://parser:8090。
     */
    @Value("${sidecar.parser-url}")
    private String parserBaseUrl;

    private final RestTemplate restTemplate;

    public ParserClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * 调用 Python 解析侧车解析文档，返回 chunk 列表。
     * <p>
     * multipart 请求构建方式：外层设置 Content-Type 为 multipart/form-data；
     * file 字段使用 ByteArrayResource 包装文件字节，并重写 getFilename()，
     * 让 RestTemplate 按文件 Part 发送；file_type 字段作为普通表单字段传入。
     *
     * @param fileBytes 文件字节数组
     * @param fileType  文件类型，支持 pdf / docx
     * @return 解析后的 chunk 列表
     */
    public List<ChunkDTO> parse(byte[] fileBytes, String fileType) {
        String url = parserBaseUrl + "/parse";
        log.info("调用 Python 解析侧车: url={}, fileType={}, fileSize={} bytes",
                url, fileType, fileBytes.length);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            ByteArrayResource fileResource = new ByteArrayResource(fileBytes) {
                @Override
                public String getFilename() {
                    return "file." + fileType;
                }
            };
            body.add("file", fileResource);
            body.add("file_type", fileType);

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
            ResponseEntity<ParseResponse> response = restTemplate.postForEntity(
                    url, requestEntity, ParseResponse.class);

            if (response.getStatusCode() != HttpStatus.OK) {
                log.warn("Python 解析侧车返回非 200 状态: url={}, status={}", url, response.getStatusCode());
                throw BusinessException.of(4002, "解析服务返回错误");
            }

            ParseResponse responseBody = response.getBody();
            if (responseBody == null || responseBody.getChunks() == null) {
                log.warn("Python 解析侧车返回空结果: url={}, fileType={}", url, fileType);
                return Collections.emptyList();
            }

            List<ChunkDTO> chunks = responseBody.getChunks();
            log.info("Python 解析侧车返回: chunkCount={}", chunks.size());
            return chunks;
        } catch (ResourceAccessException e) {
            // 连接失败、连接超时、读取超时都属于 I/O 不可达场景，按侧车不可用处理。
            log.error("Python 解析侧车不可用: url={}, fileType={}, error={}",
                    url, fileType, e.getMessage());
            throw BusinessException.of(4001, "解析服务不可用");
        } catch (HttpStatusCodeException e) {
            // HTTP 4xx/5xx 表示侧车已响应但解析失败，按解析服务返回错误处理。
            log.error("Python 解析侧车返回错误: url={}, fileType={}, status={}, body={}",
                    url, fileType, e.getStatusCode(), e.getResponseBodyAsString());
            throw BusinessException.of(4002, "解析服务返回错误");
        } catch (RestClientException e) {
            // 其他 RestTemplate 异常保留为兜底解析失败，避免泄露底层实现细节。
            log.error("调用 Python 解析侧车失败: url={}, fileType={}, error={}",
                    url, fileType, e.getMessage());
            throw BusinessException.of(4003, "文档解析失败");
        }
    }

    /**
     * 检查 Python 解析侧车是否存活。
     * <p>
     * 此方法可在文档上传前选择性调用，用于快速判断侧车服务是否可用，
     * 避免用户提交后才在异步任务中发现解析服务不可达。
     *
     * @return true 表示侧车可用，false 表示不可用
     */
    public boolean checkHealth() {
        String url = parserBaseUrl + "/health";
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (RestClientException e) {
            log.warn("Python 解析侧车健康检查失败: url={}, error={}", url, e.getMessage());
            return false;
        }
    }

    /**
     * Python 侧车响应体。
     * <p>
     * 仅映射当前 Java 流程需要的 chunks 数组，其余响应字段由 Jackson 忽略。
     */
    @Data
    private static class ParseResponse {
        private List<ChunkDTO> chunks;
    }
}
