// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.controller;

import com.nexarank.api.model.ZeroResultQuery;
import com.nexarank.api.repository.ZeroResultQueryRepository;
import com.nexarank.api.security.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/zero-results")
public class ZeroResultController {

    private final ZeroResultQueryRepository repository;

    public ZeroResultController(ZeroResultQueryRepository repository) {
        this.repository = repository;
    }

    @PostMapping
    public ResponseEntity<?> recordZeroResult(@RequestBody Map<String, Object> body) {
        ZeroResultQuery zrq = new ZeroResultQuery();
        zrq.setId(UUID.randomUUID().toString());
        zrq.setTenantId(TenantContext.getTenantId());
        zrq.setProjectId(TenantContext.getProjectId());
        zrq.setQuery((String) body.getOrDefault("query", ""));
        zrq.setSessionId((String) body.get("sessionId"));
        zrq.setOccurredAt(Instant.now());
        repository.save(zrq);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", zrq.getId()));
    }
}
