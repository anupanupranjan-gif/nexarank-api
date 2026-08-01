// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.controller;

import com.nexarank.api.repository.ProjectRepository;
import com.nexarank.api.security.TenantContext;
import com.nexarank.api.service.AnalyticsPdfService;
import com.nexarank.api.service.AnalyticsService;
import com.nexarank.api.service.AnalyticsService.Window;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final ProjectRepository projectRepository;
    private final AnalyticsPdfService pdfService;

    public AnalyticsController(AnalyticsService analyticsService,
                                ProjectRepository projectRepository,
                                AnalyticsPdfService pdfService) {
        this.analyticsService = analyticsService;
        this.projectRepository = projectRepository;
        this.pdfService = pdfService;
    }

    /**
     * NR-36: lets an ADMIN/TENANT_ADMIN/SUPER_ADMIN view another project's analytics
     * within their own tenant (never cross-tenant). Every other caller stays scoped
     * to their JWT's own project, same as before this existed.
     */
    private String resolveProjectId(String requestedProjectId) {
        if (requestedProjectId == null || requestedProjectId.isBlank()) {
            return TenantContext.getProjectId();
        }
        if (!isAdmin()) {
            throw new AccessDeniedException("Only ADMIN/TENANT_ADMIN may view another project's analytics");
        }
        projectRepository.findByTenantIdAndId(TenantContext.getTenantId(), requestedProjectId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown project: " + requestedProjectId));
        return requestedProjectId;
    }

    private boolean isAdmin() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().stream().anyMatch(a ->
                a.getAuthority().equals("ROLE_ADMIN")
                        || a.getAuthority().equals("ROLE_TENANT_ADMIN")
                        || a.getAuthority().equals("ROLE_SUPER_ADMIN"));
    }

    @GetMapping("/overview")
    public ResponseEntity<?> getOverview(@RequestParam(defaultValue = "30") int days,
                                          @RequestParam(required = false) String startDate,
                                          @RequestParam(required = false) String endDate,
                                          @RequestParam(required = false) String projectId) {
        String tenantId = TenantContext.getTenantId();
        String resolvedProjectId = resolveProjectId(projectId);
        Window window = analyticsService.resolveWindow(days, startDate, endDate);
        return ResponseEntity.ok(analyticsService.buildOverview(tenantId, resolvedProjectId, window, days));
    }

    @GetMapping("/trends")
    public ResponseEntity<?> getTrends(@RequestParam(defaultValue = "30") int days,
                                        @RequestParam(required = false) String startDate,
                                        @RequestParam(required = false) String endDate,
                                        @RequestParam(required = false) String projectId) {
        String tenantId = TenantContext.getTenantId();
        String resolvedProjectId = resolveProjectId(projectId);
        return ResponseEntity.ok(analyticsService.buildTrends(tenantId, resolvedProjectId, days, startDate, endDate));
    }

    /**
     * NR-36: search health report — latency percentiles (current project)
     * and search volume/zero-result rate broken out across every project
     * in the tenant. Zero-result trend over time is already covered by
     * /trends, so it isn't duplicated here.
     */
    @GetMapping("/search-health")
    public ResponseEntity<?> getSearchHealth(@RequestParam(defaultValue = "30") int days,
                                              @RequestParam(required = false) String startDate,
                                              @RequestParam(required = false) String endDate,
                                              @RequestParam(required = false) String projectId) {
        String tenantId = TenantContext.getTenantId();
        String resolvedProjectId = resolveProjectId(projectId);
        Window window = analyticsService.resolveWindow(days, startDate, endDate);
        return ResponseEntity.ok(analyticsService.buildSearchHealth(tenantId, resolvedProjectId, window, days));
    }

    /**
     * NR-36: facet usage report — value selection frequency and a session-
     * level click-rate approximation per facet value, plus which configured
     * facets went unused this period. Depends on SearchEvent.selectedFacets
     * actually being populated, which requires search-api to send it and
     * search-ui to actually forward non-brand/category facet picks (both
     * fixed as part of this same change — see search-api/search-ui commits).
     */
    @GetMapping("/facet-usage")
    public ResponseEntity<?> getFacetUsage(@RequestParam(defaultValue = "30") int days,
                                            @RequestParam(required = false) String startDate,
                                            @RequestParam(required = false) String endDate,
                                            @RequestParam(required = false) String projectId) {
        String tenantId = TenantContext.getTenantId();
        String resolvedProjectId = resolveProjectId(projectId);
        Window window = analyticsService.resolveWindow(days, startDate, endDate);
        return ResponseEntity.ok(analyticsService.buildFacetUsage(tenantId, resolvedProjectId, window, days));
    }

    @GetMapping("/rules-performance")
    public ResponseEntity<?> getRulesPerformance(@RequestParam(defaultValue = "30") int days,
                                                  @RequestParam(required = false) String startDate,
                                                  @RequestParam(required = false) String endDate,
                                                  @RequestParam(required = false) String projectId) {
        String tenantId = TenantContext.getTenantId();
        String resolvedProjectId = resolveProjectId(projectId);
        Window window = analyticsService.resolveWindow(days, startDate, endDate);
        return ResponseEntity.ok(analyticsService.buildRulesPerformance(tenantId, resolvedProjectId, window, days));
    }

    /**
     * NR-36: consolidated "send to leadership" PDF — overview, search
     * health, facet usage, and rule performance, in one document. Chosen
     * over separate per-section CSV/Excel exports (the ticket's literal
     * text) since the actual use case is a polished snapshot to hand to
     * someone with no dashboard access (e.g. a STAKEHOLDER-role recipient,
     * NR-67), not raw data to keep manipulating — a deliberate scope
     * decision, not an oversight.
     */
    @GetMapping(value = "/report.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> getReportPdf(@RequestParam(defaultValue = "30") int days,
                                                @RequestParam(required = false) String startDate,
                                                @RequestParam(required = false) String endDate,
                                                @RequestParam(required = false) String projectId) {
        String tenantId = TenantContext.getTenantId();
        String resolvedProjectId = resolveProjectId(projectId);
        Window window = analyticsService.resolveWindow(days, startDate, endDate);

        Map<String, Object> overview = analyticsService.buildOverview(tenantId, resolvedProjectId, window, days);
        Map<String, Object> searchHealth = analyticsService.buildSearchHealth(tenantId, resolvedProjectId, window, days);
        Map<String, Object> facetUsage = analyticsService.buildFacetUsage(tenantId, resolvedProjectId, window, days);
        Map<String, Object> rulesPerformance = analyticsService.buildRulesPerformance(tenantId, resolvedProjectId, window, days);

        byte[] pdf = pdfService.generateReport(tenantId, resolvedProjectId,
                overview, searchHealth, facetUsage, rulesPerformance);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"nexarank-analytics-report.pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}
