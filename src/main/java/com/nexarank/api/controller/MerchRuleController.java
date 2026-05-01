package com.nexarank.api.controller;

import com.nexarank.api.model.MerchRule;
import com.nexarank.api.service.MerchRuleService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/rules")
@CrossOrigin(origins = "*")
public class MerchRuleController {

    private final MerchRuleService service;

    public MerchRuleController(MerchRuleService service) {
        this.service = service;
    }

    @GetMapping
    public List<MerchRule> getAllRules() {
        return service.getAllRules();
    }

    @GetMapping("/query/{query}")
    public List<MerchRule> getRulesForQuery(@PathVariable String query) {
        return service.getRulesForQuery(query);
    }

    @PostMapping
    public MerchRule createRule(@RequestBody MerchRule rule) {
        return service.createRule(rule);
    }

    @PutMapping("/{id}")
    public ResponseEntity<MerchRule> updateRule(@PathVariable String id,
                                                 @RequestBody MerchRule rule) {
        return service.updateRule(id, rule)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRule(@PathVariable String id) {
        service.deleteRule(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/toggle")
    public ResponseEntity<Void> toggleRule(@PathVariable String id,
                                            @RequestParam boolean enabled) {
        service.toggleRule(id, enabled);
        return ResponseEntity.ok().build();
    }
}
