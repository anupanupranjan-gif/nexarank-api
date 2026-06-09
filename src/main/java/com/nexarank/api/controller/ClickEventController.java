// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.controller;

import com.nexarank.api.model.ClickEvent;
import com.nexarank.api.repository.ClickEventRepository;
import com.nexarank.api.security.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/clicks")
public class ClickEventController {

    private final ClickEventRepository clickEventRepository;

    public ClickEventController(ClickEventRepository clickEventRepository) {
        this.clickEventRepository = clickEventRepository;
    }

    @PostMapping
    public ResponseEntity<?> recordClick(@RequestBody Map<String, Object> body) {
        ClickEvent event = new ClickEvent();
        event.setId(UUID.randomUUID().toString());
        event.setTenantId(TenantContext.getTenantId());
        event.setProjectId(TenantContext.getProjectId());
        event.setSessionId((String) body.get("sessionId"));
        event.setQuery((String) body.getOrDefault("query", ""));
        event.setProductId((String) body.get("productId"));
        event.setProductTitle((String) body.get("productTitle"));
        if (body.get("position") != null) {
            event.setPosition(((Number) body.get("position")).intValue());
        }
        event.setClickedAt(Instant.now());
        clickEventRepository.save(event);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", event.getId()));
    }
}
