// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.controller;

import com.nexarank.api.model.WatchedQuery;
import com.nexarank.api.service.WatchedQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * CRUD for watched queries.
 *
 * GET    /api/v1/suggestions/watched-queries       — list all
 * POST   /api/v1/suggestions/watched-queries       — add/update
 * DELETE /api/v1/suggestions/watched-queries/{id}  — remove
 * PATCH  /api/v1/suggestions/watched-queries/{id}  — toggle enabled
 */
@RestController
@RequestMapping("/api/v1/suggestions/watched-queries")
public class WatchedQueryController {

    private final WatchedQueryService service;

    public WatchedQueryController(WatchedQueryService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<WatchedQuery>> getAll() {
        return ResponseEntity.ok(service.getAll());
    }

    @PostMapping
    public ResponseEntity<WatchedQuery> add(@RequestBody WatchedQuery wq) {
        return ResponseEntity.ok(service.add(wq));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/toggle")
    public ResponseEntity<?> toggle(@PathVariable String id) {
        return service.toggleEnabled(id)
            .<ResponseEntity<?>>map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
}
