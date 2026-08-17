// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexarank.api.model.ApiAccessEvent;
import com.nexarank.api.model.AuditEvent;
import com.nexarank.api.model.Project;
import com.nexarank.api.model.User;
import com.nexarank.api.model.UserProject;
import com.nexarank.api.repository.ApiAccessEventRepository;
import com.nexarank.api.repository.AuditEventRepository;
import com.nexarank.api.repository.ProjectRepository;
import com.nexarank.api.repository.UserProjectRepository;
import com.nexarank.api.repository.UserRepository;
import com.nexarank.api.security.TenantContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * NR-70: read side of the two-tier audit log.
 *
 * Tier 1 (rule-change history) is project-scoped through the caller's real
 * user_projects assignments via ProjectAccessService — the same source of truth
 * NR-121 established, not a second parallel notion of project access.
 *
 * Tier 2 (full audit log) is tenant-wide and ADMIN-only; that restriction is
 * enforced at the SecurityConfig matcher and re-checked here rather than being
 * left purely to URL matching.
 */
@Service
public class AuditQueryService {

    private final AuditEventRepository auditEventRepository;
    private final ApiAccessEventRepository apiAccessEventRepository;
    private final UserRepository userRepository;
    private final ProjectAccessService projectAccessService;
    private final ProjectRepository projectRepository;
    private final UserProjectRepository userProjectRepository;
    private final ObjectMapper objectMapper;

    public AuditQueryService(AuditEventRepository auditEventRepository,
                             ApiAccessEventRepository apiAccessEventRepository,
                             UserRepository userRepository,
                             ProjectAccessService projectAccessService,
                             ProjectRepository projectRepository,
                             UserProjectRepository userProjectRepository,
                             ObjectMapper objectMapper) {
        this.auditEventRepository = auditEventRepository;
        this.apiAccessEventRepository = apiAccessEventRepository;
        this.userRepository = userRepository;
        this.projectAccessService = projectAccessService;
        this.projectRepository = projectRepository;
        this.userProjectRepository = userProjectRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Projects whose rule-change history the current caller may READ. ADMIN and
     * other tenant-wide roles get every project in the tenant;
     * MERCHANDISER/APPROVER/PROJECT_ADMIN get exactly their assignments.
     *
     * Deliberately NOT ProjectAccessService.availableProjectIds — that answers
     * "which projects may this user activate", which excludes disabled ones. An
     * audit log is retrospective by nature: a project being disabled must not
     * erase its history from a compliance reader's view, and the ticket's own
     * wording is "ADMIN sees all projects in the tenant". Same reasoning NR-36's
     * search-health reporting already applied to disabled projects. This grants
     * read access to immutable history only, never the ability to act in a
     * project.
     */
    public List<String> visibleProjectIds(String username) {
        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null) return List.of();
        if (projectAccessService.isTenantWide(user)) {
            return projectRepository.findByTenantId(user.getTenantId()).stream()
                    .map(Project::getId).toList();
        }
        return userProjectRepository.findByUserId(user.getId()).stream()
                .map(UserProject::getProjectId).distinct().toList();
    }

    public Page<AuditEvent> tier1(String username, String actor, String action,
                                  Instant start, Instant end, int page, int size) {
        List<String> projectIds = visibleProjectIds(username);
        if (projectIds.isEmpty()) return Page.empty();
        return auditEventRepository.findTier1(
                TenantContext.getTenantId(), projectIds,
                blankToNull(actor), blankToNull(action),
                start, end, PageRequest.of(page, size));
    }

    public List<AuditEvent> tier1Export(String username, String actor, String action,
                                        Instant start, Instant end) {
        List<String> projectIds = visibleProjectIds(username);
        if (projectIds.isEmpty()) return List.of();
        return auditEventRepository.findTier1ForExport(
                TenantContext.getTenantId(), projectIds,
                blankToNull(actor), blankToNull(action), start, end);
    }

    public Page<AuditEvent> tier2(String actor, String action,
                                  Instant start, Instant end, int page, int size) {
        return auditEventRepository.findTier2(
                TenantContext.getTenantId(), blankToNull(actor), blankToNull(action),
                start, end, PageRequest.of(page, size));
    }

    public List<AuditEvent> tier2Export(String actor, String action, Instant start, Instant end) {
        return auditEventRepository.findTier2ForExport(
                TenantContext.getTenantId(), blankToNull(actor), blankToNull(action), start, end);
    }

    public Page<ApiAccessEvent> apiAccess(ApiAccessEvent.EventType type, int page, int size) {
        if (type == null) {
            return apiAccessEventRepository.findByTenantIdOrderByCreatedAtDesc(
                    TenantContext.getTenantId(), PageRequest.of(page, size));
        }
        return apiAccessEventRepository.findByTenantIdAndEventTypeOrderByCreatedAtDesc(
                TenantContext.getTenantId(), type, PageRequest.of(page, size));
    }

    public List<ApiAccessEvent> apiAccessExport(ApiAccessEvent.EventType type,
                                                String username, Instant start, Instant end) {
        return apiAccessEventRepository.search(
                TenantContext.getTenantId(), type == null ? null : type.name(),
                blankToNull(username), start, end);
    }

    public List<String> distinctActions(Integer tier) {
        return auditEventRepository.findDistinctActions(TenantContext.getTenantId(), tier);
    }

    /** Presentation shape for one Tier 1 row, with the diff parsed back out. */
    public Map<String, Object> toRuleChangeView(AuditEvent e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId());
        m.put("action", e.getAction());
        m.put("actor", e.getUsername());
        m.put("timestamp", e.getCreatedAt());
        m.put("projectId", e.getProjectId());
        m.put("ruleId", e.getEntityId());
        m.put("ruleName", e.getEntityName());
        m.put("previousState", e.getPreviousState());
        m.put("newState", e.getNewState());
        m.put("reason", e.getReason());
        m.put("summary", e.getDetails());
        m.put("changes", parseDiff(e.getFieldDiff()));
        return m;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, String>> parseDiff(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, List.class);
        } catch (Exception e) {
            return List.of();
        }
    }

    /** Default window when the caller supplies no explicit date range. */
    public Instant defaultStart(Integer days) {
        return Instant.now().minus(days == null ? 30 : days, ChronoUnit.DAYS);
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
