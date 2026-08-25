// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.controller;

import com.nexarank.api.configexport.ConfigImportService;
import com.nexarank.api.configexport.dto.ImportSummary;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * NR-157/NR-166/NR-167: imports a config export ZIP (NR-165's format) into
 * the current tenant+project. See ConfigImportGateService for the hard
 * precondition (engine config + LLM config must already be valid) and
 * ConfigImportService for per-category apply semantics.
 */
@RestController
@RequestMapping("/api/v1/config-import")
public class ConfigImportController {

    private final ConfigImportService importService;

    public ConfigImportController(ConfigImportService importService) {
        this.importService = importService;
    }

    @PostMapping
    public ResponseEntity<ImportSummary> importConfig(@RequestParam("file") MultipartFile file) {
        Map<String, byte[]> files = unzip(file);
        return ResponseEntity.ok(importService.importBundle(files));
    }

    private Map<String, byte[]> unzip(MultipartFile file) {
        Map<String, byte[]> files = new LinkedHashMap<>();
        try (ZipInputStream zis = new ZipInputStream(file.getInputStream())) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                files.put(entry.getName(), zis.readAllBytes());
                zis.closeEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read uploaded config import file", e);
        }
        return files;
    }
}
