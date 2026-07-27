// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.controller;

import com.nexarank.api.model.ContentRule;
import com.nexarank.api.model.ContentZone;
import com.nexarank.api.model.ErrorResponse;
import com.nexarank.api.service.ContentRuleService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/content-rules")
public class ContentRuleController {

    private final ContentRuleService service;

    public ContentRuleController(ContentRuleService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<?> list(
            @RequestParam(required = false) ContentZone zone,
            @RequestParam(required = false) ContentRule.ContentRuleStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(service.list(zone, status, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable String id) {
        return service.getById(id)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ErrorResponse.of("CONTENT_RULE_NOT_FOUND", "Content rule not found: " + id)));
    }

    @PostMapping
    public ResponseEntity<ContentRule> createRule(@RequestBody ContentRule rule) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createRule(rule));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateRule(@PathVariable String id, @RequestBody ContentRule rule) {
        return service.updateRule(id, rule)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ErrorResponse.of("CONTENT_RULE_NOT_FOUND", "Content rule not found: " + id)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRule(@PathVariable String id) {
        service.deleteRule(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/submit")
    public ResponseEntity<?> submitForReview(@PathVariable String id) {
        return service.submitForReview(id)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ErrorResponse.of("CONTENT_RULE_NOT_FOUND", "Content rule not found: " + id)));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<?> approveRule(@PathVariable String id,
                                          @RequestBody(required = false) Map<String, String> body) {
        String comment = body != null ? body.getOrDefault("comment", "") : "";
        return service.approveRule(id, comment)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ErrorResponse.of("CONTENT_RULE_NOT_FOUND", "Content rule not found: " + id)));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<?> rejectRule(@PathVariable String id,
                                         @RequestBody(required = false) Map<String, String> body) {
        String comment = body != null ? body.getOrDefault("comment", "") : "";
        return service.rejectRule(id, comment)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ErrorResponse.of("CONTENT_RULE_NOT_FOUND", "Content rule not found: " + id)));
    }

    @GetMapping("/{id}/versions")
    public ResponseEntity<?> getHistory(@PathVariable String id) {
        return ResponseEntity.ok(service.getHistory(id));
    }
}
