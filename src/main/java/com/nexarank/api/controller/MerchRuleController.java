// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.controller;

import com.nexarank.api.model.MerchRule;
import com.nexarank.api.service.MerchRuleService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/rules")
public class MerchRuleController {

    private final MerchRuleService service;

    public MerchRuleController(MerchRuleService service) {
        this.service = service;
    }

    @GetMapping
    public List<MerchRule> getAllRules() {
        return service.getAllRules();
    }

    @GetMapping("/pending")
    public List<MerchRule> getPendingRules() {
        return service.getPendingRules();
    }

    @GetMapping("/query/{query}")
    public List<MerchRule> getRulesByQuery(@PathVariable String query) {
        return service.getRulesByQuery(query);
    }

    @GetMapping("/{id}")
    public ResponseEntity<MerchRule> getById(@PathVariable String id) {
        return service.getById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<MerchRule> createRule(@RequestBody MerchRule rule) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createRule(rule));
    }

    @PutMapping("/{id}")
    public ResponseEntity<MerchRule> updateRule(@PathVariable String id, @RequestBody MerchRule rule) {
        return service.updateRule(id, rule)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}/toggle")
    public ResponseEntity<MerchRule> toggleRule(@PathVariable String id) {
        return service.toggleRule(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}/approve")
    public ResponseEntity<MerchRule> approveRule(@PathVariable String id,
                                                  @RequestBody(required = false) Map<String, String> body) {
        String comment = body != null ? body.getOrDefault("comment", "") : "";
        return service.approveRule(id, comment)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}/reject")
    public ResponseEntity<MerchRule> rejectRule(@PathVariable String id,
                                                 @RequestBody(required = false) Map<String, String> body) {
        String comment = body != null ? body.getOrDefault("comment", "") : "";
        return service.rejectRule(id, comment)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRule(@PathVariable String id) {
        service.deleteRule(id);
        return ResponseEntity.noContent().build();
    }
}
