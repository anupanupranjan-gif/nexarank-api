// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api;

import com.nexarank.api.service.FacetConfigService;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class NexaRankApplication {

    public static void main(String[] args) {
        SpringApplication.run(NexaRankApplication.class, args);
    }

    @Bean
    public ApplicationRunner seedFacets(FacetConfigService facetConfigService) {
        return args -> facetConfigService.seedDefaultFacets();
    }
}