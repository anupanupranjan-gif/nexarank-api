// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexarank.api.configexport.ConfigExportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * NR-157/NR-165: exports the current tenant+project's configuration as a
 * downloadable ZIP — one JSON file per category (see ConfigExportService),
 * matching resolved decision #4 (v1 is a pure file download, no direct
 * GitHub integration — the tenant pushes it wherever they want themselves).
 */
@RestController
@RequestMapping("/api/v1/config-export")
public class ConfigExportController {

    private final ConfigExportService exportService;
    private final ObjectMapper objectMapper;

    public ConfigExportController(ConfigExportService exportService, ObjectMapper objectMapper) {
        this.exportService = exportService;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public ResponseEntity<byte[]> export() {
        Map<String, Object> bundle = exportService.exportAll();
        byte[] zip = toZip(bundle);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"nexarank-config-export.zip\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(zip);
    }

    private byte[] toZip(Map<String, Object> bundle) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (Map.Entry<String, Object> entry : bundle.entrySet()) {
                zos.putNextEntry(new ZipEntry(entry.getKey()));
                zos.write(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(entry.getValue()));
                zos.closeEntry();
            }
            zos.finish();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to build config export ZIP", e);
        }
    }
}
