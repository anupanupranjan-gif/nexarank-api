// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.config;

import org.apache.http.ssl.SSLContextBuilder;
import org.elasticsearch.client.RestClientBuilder;
import org.springframework.boot.autoconfigure.elasticsearch.RestClientBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.net.ssl.SSLContext;

@Configuration
public class ElasticsearchConfig {

    @Bean
    public RestClientBuilderCustomizer restClientBuilderCustomizer() {
        return new RestClientBuilderCustomizer() {
            @Override
            public void customize(RestClientBuilder builder) {
            }

            @Override
            public void customize(org.apache.http.impl.nio.client.HttpAsyncClientBuilder builder) {
                try {
                    SSLContext sslContext = SSLContextBuilder.create()
                            .loadTrustMaterial(null, (chains, authType) -> true)
                            .build();
                    builder.setSSLContext(sslContext);
                    builder.setSSLHostnameVerifier((host, session) -> true);
                } catch (Exception e) {
                    throw new RuntimeException("Failed to configure SSL", e);
                }
            }
        };
    }
}
