package com.example.CRUD.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.client.ClientConfiguration;
import org.springframework.data.elasticsearch.client.elc.ReactiveElasticsearchClient;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchClients;
import org.springframework.data.elasticsearch.repository.config.EnableReactiveElasticsearchRepositories;

@Configuration
@EnableReactiveElasticsearchRepositories(basePackages = "com.example.CRUD.repository")
public class ElasticsearchConfig {

    @Value("${spring.elasticsearch.uris}")
    private String elasticUri;

    @Value("${spring.elasticsearch.username}")
    private String elasticUsername;

    @Value("${spring.elasticsearch.password}")
    private String elasticPassword;


    @Bean
    public ReactiveElasticsearchClient reactiveElasticsearchClient() {
        ClientConfiguration clientConfiguration = ClientConfiguration.builder()
                .connectedTo(elasticUri)
                .withBasicAuth(elasticUsername, elasticPassword)
                .build();

        return ElasticsearchClients.createReactive(clientConfiguration);
    }
}
