package com.kb.app.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class ParserRestClientConfig {

    @Bean("parserRestClient")
    public RestClient parserRestClient(@Value("${sidecar.parser-url}") String parserUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(120));

        return RestClient.builder()
                .baseUrl(parserUrl)
                .requestFactory(requestFactory)
                .build();
    }
}
