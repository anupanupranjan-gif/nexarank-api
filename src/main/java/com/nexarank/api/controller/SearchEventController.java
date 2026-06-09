// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.controller;

import com.nexarank.api.model.SearchEvent;
import com.nexarank.api.repository.SearchEventRepository;
import com.nexarank.api.security.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/search-events")
public class SearchEventController {

    private final SearchEventRepository repository;

    public SearchEventController(SearchEventRepository repository) {
        this.repository = repository;
    }

    @PostMapping
    public ResponseEntity<?> recordSearchEvent(@RequestBody Map<String, Object> body) {
        SearchEvent event = new SearchEvent();
        event.setId(UUID.randomUUID().toString());
        event.setTenantId(TenantContext.getTenantId());
        event.setProjectId(TenantContext.getProjectId());
        event.setSessionId((String) body.get("sessionId"));
        event.setQuery((String) body.getOrDefault("query", ""));
        event.setResultCount(body.get("resultCount") != null
                ? ((Number) body.get("resultCount")).intValue() : 0);
        event.setMode((String) body.get("mode"));
        event.setTookMs(body.get("tookMs") != null
                ? ((Number) body.get("tookMs")).intValue() : null);
        event.setSearchedAt(Instant.now());
        repository.save(event);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", event.getId()));
    }
}
