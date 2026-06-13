// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.controller;

import com.nexarank.api.model.Stopword;
import com.nexarank.api.service.StopwordService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * CRUD API for managing stopwords per tenant/project.
 *
 * GET    /api/v1/pipeline/stopwords          — list all stopwords
 * POST   /api/v1/pipeline/stopwords          — add a single stopword
 * POST   /api/v1/pipeline/stopwords/bulk     — add multiple stopwords
 * DELETE /api/v1/pipeline/stopwords/{word}   — remove a stopword
 */
@RestController
@RequestMapping("/api/v1/pipeline/stopwords")
public class StopwordController {

    private final StopwordService service;

    public StopwordController(StopwordService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<Stopword>> getAll() {
        return ResponseEntity.ok(service.getAll());
    }

    @PostMapping
    public ResponseEntity<Stopword> add(@RequestBody Map<String, String> body) {
        String word = body.get("word");
        if (word == null || word.isBlank())
            return ResponseEntity.badRequest().build();
        return ResponseEntity.ok(service.add(word));
    }

    @PostMapping("/bulk")
    public ResponseEntity<Void> addBulk(@RequestBody Map<String, List<String>> body) {
        List<String> words = body.get("words");
        if (words == null || words.isEmpty())
            return ResponseEntity.badRequest().build();
        service.addBulk(words);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{word}")
    public ResponseEntity<Void> delete(@PathVariable String word) {
        service.delete(word);
        return ResponseEntity.noContent().build();
    }
}
