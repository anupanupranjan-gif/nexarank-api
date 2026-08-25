// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.controller;

import com.nexarank.api.model.Project;
import com.nexarank.api.model.Tenant;
import com.nexarank.api.repository.ContentRuleRepository;
import com.nexarank.api.repository.FacetConfigRepository;
import com.nexarank.api.repository.FacetVisibilityRuleRepository;
import com.nexarank.api.repository.LlmConfigRepository;
import com.nexarank.api.repository.MerchRuleRepository;
import com.nexarank.api.repository.ProjectRepository;
import com.nexarank.api.repository.RuleAbTestRepository;
import com.nexarank.api.repository.SearchEngineConfigRepository;
import com.nexarank.api.repository.TenantRepository;
import com.nexarank.api.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
public class TenantController {

    private final TenantRepository tenantRepository;
    private final ProjectRepository projectRepository;
    private final MerchRuleRepository merchRuleRepository;
    private final ContentRuleRepository contentRuleRepository;
    private final FacetConfigRepository facetConfigRepository;
    private final FacetVisibilityRuleRepository facetVisibilityRuleRepository;
    private final RuleAbTestRepository ruleAbTestRepository;
    private final SearchEngineConfigRepository searchEngineConfigRepository;
    private final LlmConfigRepository llmConfigRepository;
    private final UserRepository userRepository;

    public TenantController(TenantRepository tenantRepository, ProjectRepository projectRepository,
                             MerchRuleRepository merchRuleRepository, ContentRuleRepository contentRuleRepository,
                             FacetConfigRepository facetConfigRepository,
                             FacetVisibilityRuleRepository facetVisibilityRuleRepository,
                             RuleAbTestRepository ruleAbTestRepository,
                             SearchEngineConfigRepository searchEngineConfigRepository,
                             LlmConfigRepository llmConfigRepository,
                             UserRepository userRepository) {
        this.tenantRepository = tenantRepository;
        this.projectRepository = projectRepository;
        this.merchRuleRepository = merchRuleRepository;
        this.contentRuleRepository = contentRuleRepository;
        this.facetConfigRepository = facetConfigRepository;
        this.facetVisibilityRuleRepository = facetVisibilityRuleRepository;
        this.ruleAbTestRepository = ruleAbTestRepository;
        this.searchEngineConfigRepository = searchEngineConfigRepository;
        this.llmConfigRepository = llmConfigRepository;
        this.userRepository = userRepository;
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
            if (body.containsKey("autoPublishRules")) tenant.setAutoPublishRules(Boolean.parseBoolean(body.get("autoPublishRules")));
            // NR-129: comma-separated CORS allow-list for this tenant's storefront
            // origin(s) — lets a future tenant's browser-facing integration be
            // enabled with a data change here, not a code change/redeploy.
            if (body.containsKey("allowedOrigins")) tenant.setAllowedOrigins(body.get("allowedOrigins"));
            if (body.containsKey("region")) {
                try {
                    tenant.setRegion(com.nexarank.api.model.TenantRegion.valueOf(body.get("region").toUpperCase()));
                } catch (IllegalArgumentException e) {
                    return ResponseEntity.badRequest().body(Map.of("error",
                            "Unknown region: " + body.get("region") + " (expected US, EU, or APAC)"));
                }
            }
            // NR-155: was a real column with no way to actually set it - the data
            // model already supported a different retention window per tenant
            // (needed since regulatory minimums/maximums differ by jurisdiction/
            // industry), but nothing exposed it. Which specific number a given
            // tenant needs is a legal/product call (see NR-140/141), not something
            // to hardcode here - this only enforces sane bounds (positive, capped
            // at 10 years) so the field can't be set to something nonsensical.
            if (body.containsKey("auditRetentionDays")) {
                int days;
                try {
                    days = Integer.parseInt(body.get("auditRetentionDays"));
                } catch (NumberFormatException e) {
                    return ResponseEntity.badRequest().body(Map.of("error",
                            "auditRetentionDays must be an integer"));
                }
                if (days < 1 || days > 3650) {
                    return ResponseEntity.badRequest().body(Map.of("error",
                            "auditRetentionDays must be between 1 and 3650 days"));
                }
                tenant.setAuditRetentionDays(days);
            }
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

    /**
     * Hard delete — only allowed when the project has no data left in it.
     * There was no DELETE mapping for this path at all before, so every
     * DELETE call here got the generic HttpRequestMethodNotSupportedException
     * -> GlobalExceptionHandler's catch-all Exception handler, which
     * reported it as an opaque 500 instead of a real 404/405.
     *
     * A real hard delete has to check every table with a project_id before
     * touching the row: user_projects cascade-deletes on its own (V38), but
     * merch_rules/content_rules/facet_config/facet_visibility_rules/
     * rule_ab_tests/engine_config/llm_config do not — several of those are
     * FK-constrained to projects(id) with no ON DELETE clause, so an
     * unconditional delete would either fail with a raw constraint violation
     * (another opaque 500) or, for the tables that aren't FK-constrained
     * (rule_ab_tests), silently leave orphaned rows behind. Rejecting with a
     * clear 409 when anything still references the project is safer than
     * cascading real data away implicitly — the existing PUT .../projects/
     * {projectId} with {"enabled": false} is the soft-delete path for a
     * project that still has data in it.
     */
    @DeleteMapping("/tenants/{tenantId}/projects/{projectId}")
    public ResponseEntity<?> deleteProject(@PathVariable String tenantId, @PathVariable String projectId) {
        return projectRepository.findByTenantIdAndId(tenantId, projectId).map(project -> {
            List<String> blockers = new ArrayList<>();
            if (!merchRuleRepository.findByTenantIdAndProjectId(tenantId, projectId).isEmpty()) blockers.add("rules");
            if (!contentRuleRepository.findByTenantIdAndProjectIdAndDeletedAtIsNull(tenantId, projectId).isEmpty()) blockers.add("content rules");
            if (!facetConfigRepository.findByTenantIdAndProjectIdOrderBySortOrderAsc(tenantId, projectId).isEmpty()) blockers.add("facets");
            if (!facetVisibilityRuleRepository.findByTenantIdAndProjectIdOrderByPriorityDesc(tenantId, projectId).isEmpty()) blockers.add("facet visibility rules");
            if (!ruleAbTestRepository.findByTenantIdAndProjectId(tenantId, projectId).isEmpty()) blockers.add("A/B tests");
            if (searchEngineConfigRepository.findFirstByTenantIdAndProjectId(tenantId, projectId).isPresent()) blockers.add("engine config");
            if (llmConfigRepository.findFirstByTenantIdAndProjectId(tenantId, projectId).isPresent()) blockers.add("LLM config");

            if (!blockers.isEmpty()) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                        "error", "Project still has data and cannot be deleted: " + String.join(", ", blockers)
                                + ". Remove it first, or use PUT with {\"enabled\": false} to disable the project instead.",
                        "blockers", blockers));
            }

            // Nobody's left with this as their active project — resolveActiveProjectId
            // picks a different one on their next login/refresh, same as if their
            // access to it had simply been revoked.
            List<com.nexarank.api.model.User> strandedUsers = userRepository.findByLastActiveProjectId(projectId);
            strandedUsers.forEach(u -> u.setLastActiveProjectId(null));
            userRepository.saveAll(strandedUsers);

            projectRepository.delete(project); // user_projects rows cascade-delete (V38)
            return ResponseEntity.noContent().build();
        }).orElse(ResponseEntity.notFound().build());
    }
}
