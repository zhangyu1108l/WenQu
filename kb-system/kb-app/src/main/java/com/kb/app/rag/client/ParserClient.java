package com.kb.app.rag.client;

import com.kb.app.rag.dto.ChunkDTO;
import com.kb.common.exception.BusinessException;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Collections;
import java.util.List;

@Slf4j
@Component
public class ParserClient {

    private static final String PARSE_URI = "/parse";
    private static final String HEALTH_URI = "/health";

    private final RestClient parserRestClient;

    public ParserClient(@Qualifier("parserRestClient") RestClient parserRestClient) {
        this.parserRestClient = parserRestClient;
    }

    public List<ChunkDTO> parse(byte[] fileBytes, String fileType) {
        log.info("Call parser sidecar: uri={}, fileType={}, size={} bytes",
                PARSE_URI, fileType, fileBytes.length);

        try {
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            ByteArrayResource fileResource = new ByteArrayResource(fileBytes) {
                @Override
                public String getFilename() {
                    return "file." + fileType;
                }
            };
            body.add("file", fileResource);
            body.add("file_type", fileType);

            ResponseEntity<ParseResponse> response = parserRestClient.post()
                    .uri(PARSE_URI)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .toEntity(ParseResponse.class);

            if (response.getStatusCode() != HttpStatus.OK) {
                log.warn("Parser sidecar returned non-200 status: uri={}, status={}",
                        PARSE_URI, response.getStatusCode());
                throw BusinessException.of(4002, "解析服务返回错误");
            }

            ParseResponse responseBody = response.getBody();
            if (responseBody == null || responseBody.getChunks() == null) {
                log.warn("Parser sidecar returned empty body: uri={}, fileType={}", PARSE_URI, fileType);
                return Collections.emptyList();
            }

            List<ChunkDTO> chunks = responseBody.getChunks();
            log.info("Parser sidecar returned chunks: count={}", chunks.size());
            return chunks;
        } catch (ResourceAccessException e) {
            log.error("Parser sidecar unavailable: uri={}, fileType={}, error={}",
                    PARSE_URI, fileType, e.getMessage());
            throw BusinessException.of(4001, "解析服务不可用");
        } catch (HttpStatusCodeException e) {
            log.error("Parser sidecar returned error: uri={}, fileType={}, status={}, body={}",
                    PARSE_URI, fileType, e.getStatusCode(), e.getResponseBodyAsString());
            throw BusinessException.of(4002, "解析服务返回错误");
        } catch (RestClientException e) {
            log.error("Call parser sidecar failed: uri={}, fileType={}, error={}",
                    PARSE_URI, fileType, e.getMessage());
            throw BusinessException.of(4003, "文档解析失败");
        }
    }

    public boolean checkHealth() {
        try {
            ResponseEntity<String> response = parserRestClient.get()
                    .uri(HEALTH_URI)
                    .retrieve()
                    .toEntity(String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (RestClientException e) {
            log.warn("Parser sidecar health check failed: uri={}, error={}", HEALTH_URI, e.getMessage());
            return false;
        }
    }

    @Data
    private static class ParseResponse {
        private List<ChunkDTO> chunks;
    }
}
