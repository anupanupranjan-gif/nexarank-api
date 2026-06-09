// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.controller;

import com.nexarank.api.model.Project;
import com.nexarank.api.model.Tenant;
import com.nexarank.api.repository.ProjectRepository;
import com.nexarank.api.repository.TenantRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
public class TenantController {

    private final TenantRepository tenantRepository;
    private final ProjectRepository projectRepository;

    public TenantController(TenantRepository tenantRepository, ProjectRepository projectRepository) {
        this.tenantRepository = tenantRepository;
        this.projectRepository = projectRepository;
    }

    // --- Tenant endpoints ---

    @GetMapping("/tenants")
    public List<Tenant> getAllTenants() {
        return tenantRepository.findAll();
    }

    @GetMapping("/tenants/{id}")
    public ResponseEntity<?> getTenant(@PathVariable String id) {
        return tenantRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/tenants")
    public ResponseEntity<?> createTenant(@RequestBody Map<String, String> body) {
        String id = body.get("id");
        String displayName = body.get("displayName");

        if (id == null || displayName == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "id and displayName are required"));
        }

        if (tenantRepository.existsById(id)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Tenant with id '" + id + "' already exists"));
        }

        Tenant tenant = new Tenant();
        tenant.setId(id);
        tenant.setDisplayName(displayName);
        tenant.setEnabled(true);
        tenant.setCreatedAt(Instant.now());

        Tenant saved = tenantRepository.save(tenant);

        // Auto-create a default project for the tenant
        Project defaultProject = new Project();
        defaultProject.setId(UUID.randomUUID().toString());
        defaultProject.setTenantId(id);
        defaultProject.setName("Main");
        defaultProject.setEnabled(true);
        defaultProject.setCreatedAt(Instant.now());
        projectRepository.save(defaultProject);

        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    // Public endpoint — no auth required, called before login
    @GetMapping("/public/tenants/{id}/branding")
    public ResponseEntity<?> getBranding(@PathVariable String id) {
        return tenantRepository.findById(id)
                .map(t -> ResponseEntity.ok(Map.of(
                        "tenantId", t.getId(),
                        "displayName", t.getDisplayName(),
                        "logoUrl", t.getLogoUrl() != null ? t.getLogoUrl() : "",
                        "brandColor", t.getBrandColor() != null ? t.getBrandColor() : "#0077ff"
                )))
                .orElse(ResponseEntity.ok(Map.of(
                        "tenantId", id,
                        "displayName", "NexaRank",
                        "logoUrl", "",
                        "brandColor", "#0077ff"
                )));
    }

    @PutMapping("/tenants/{id}/branding")
    public ResponseEntity<?> updateBranding(@PathVariable String id, @RequestBody Map<String, String> body) {
        return tenantRepository.findById(id).map(tenant -> {
            if (body.containsKey("logoUrl")) tenant.setLogoUrl(body.get("logoUrl"));
            if (body.containsKey("brandColor")) tenant.setBrandColor(body.get("brandColor"));
            return ResponseEntity.ok(tenantRepository.save(tenant));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/tenants/{id}")
    public ResponseEntity<?> updateTenant(@PathVariable String id, @RequestBody Map<String, String> body) {
        return tenantRepository.findById(id).map(tenant -> {
            if (body.containsKey("displayName")) tenant.setDisplayName(body.get("displayName"));
            if (body.containsKey("enabled")) tenant.setEnabled(Boolean.parseBoolean(body.get("enabled")));
            return ResponseEntity.ok(tenantRepository.save(tenant));
        }).orElse(ResponseEntity.notFound().build());
    }

    // --- Project endpoints ---

    @GetMapping("/tenants/{tenantId}/projects")
    public List<Project> getProjects(@PathVariable String tenantId) {
        return projectRepository.findByTenantIdAndEnabled(tenantId, true);
    }

    @GetMapping("/tenants/{tenantId}/projects/{projectId}")
    public ResponseEntity<?> getProject(@PathVariable String tenantId, @PathVariable String projectId) {
        return projectRepository.findByTenantIdAndId(tenantId, projectId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/tenants/{tenantId}/projects")
    public ResponseEntity<?> createProject(@PathVariable String tenantId, @RequestBody Map<String, String> body) {
        if (!tenantRepository.existsById(tenantId)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Tenant '" + tenantId + "' not found"));
        }

        String name = body.get("name");
        if (name == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "name is required"));
        }

        Project project = new Project();
        project.setId(UUID.randomUUID().toString());
        project.setTenantId(tenantId);
        project.setName(name);
        project.setEnabled(true);
        project.setCreatedAt(Instant.now());

        return ResponseEntity.status(HttpStatus.CREATED).body(projectRepository.save(project));
    }

    @PutMapping("/tenants/{tenantId}/projects/{projectId}")
    public ResponseEntity<?> updateProject(@PathVariable String tenantId,
                                            @PathVariable String projectId,
                                            @RequestBody Map<String, String> body) {
        return projectRepository.findByTenantIdAndId(tenantId, projectId).map(project -> {
            if (body.containsKey("name")) project.setName(body.get("name"));
            if (body.containsKey("enabled")) project.setEnabled(Boolean.parseBoolean(body.get("enabled")));
            return ResponseEntity.ok(projectRepository.save(project));
        }).orElse(ResponseEntity.notFound().build());
    }
}
