package com.nexarank.api.config;

import org.apache.http.ssl.SSLContextBuilder;
import org.elasticsearch.client.RestClient;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;

import javax.net.ssl.SSLContext;
import java.net.URI;

@Configuration
public class ElasticsearchConfig {

    @Value("${spring.elasticsearch.uris}")
    private String esUri;

    @Value("${spring.elasticsearch.username}")
    private String username;

    @Value("${spring.elasticsearch.password}")
    private String password;

    @Bean
    public ElasticsearchClient elasticsearchClient() throws Exception {
        SSLContext sslContext = SSLContextBuilder.create()
                .loadTrustMaterial(null, (chains, authType) -> true)
                .build();

        URI uri = URI.create(esUri);
        BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(AuthScope.ANY,
                new UsernamePasswordCredentials(username, password));

        RestClient restClient = RestClient.builder(
                new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme()))
                .setHttpClientConfigCallback(httpClientBuilder ->
                        httpClientBuilder
                                .setSSLContext(sslContext)
                                .setSSLHostnameVerifier((host, session) -> true)
                                .setDefaultCredentialsProvider(credentialsProvider))
                .build();

        RestClientTransport transport = new RestClientTransport(
                restClient, new JacksonJsonpMapper());

        return new ElasticsearchClient(transport);
    }
}
