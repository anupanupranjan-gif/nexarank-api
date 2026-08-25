// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.configexport;

import com.nexarank.api.configexport.dto.*;
import com.nexarank.api.model.ContentRule;
import com.nexarank.api.model.Judgment;
import com.nexarank.api.model.JudgmentSet;
import com.nexarank.api.model.MerchRule;
import com.nexarank.api.repository.JudgmentRepository;
import com.nexarank.api.repository.JudgmentSetRepository;
import com.nexarank.api.repository.SuggestionConfigRepository;
import com.nexarank.api.repository.TenantRepository;
import com.nexarank.api.security.TenantContext;
import com.nexarank.api.service.AuditService;
import com.nexarank.api.service.ContentRuleService;
import com.nexarank.api.service.FacetConfigService;
import com.nexarank.api.service.MerchRuleService;
import com.nexarank.api.service.RuleAbTestService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * NR-157/NR-164: assembles the 7 per-category config export payloads for the
 * current tenant+project. One category = one file on disk (NR-165 ZIPs
 * these), matching resolved decision #6 — better for git diffing and
 * selective import than one giant bundle.
 *
 * Deliberately excluded per the epic: users/groups, engine config, LLM
 * config — none of the three get exported at all, not even as a template.
 */
@Service
public class ConfigExportService {

    private final MerchRuleService merchRuleService;
    private final ContentRuleService contentRuleService;
    private final FacetConfigService facetConfigService;
    private final RuleAbTestService ruleAbTestService;
    private final TenantRepository tenantRepository;
    private final JudgmentSetRepository judgmentSetRepository;
    private final JudgmentRepository judgmentRepository;
    private final SuggestionConfigRepository suggestionConfigRepository;
    private final JdbcTemplate jdbc;
    private final AuditService auditService;

    public ConfigExportService(MerchRuleService merchRuleService,
                                ContentRuleService contentRuleService,
                                FacetConfigService facetConfigService,
                                RuleAbTestService ruleAbTestService,
                                TenantRepository tenantRepository,
                                JudgmentSetRepository judgmentSetRepository,
                                JudgmentRepository judgmentRepository,
                                SuggestionConfigRepository suggestionConfigRepository,
                                JdbcTemplate jdbc,
                                AuditService auditService) {
        this.merchRuleService = merchRuleService;
        this.contentRuleService = contentRuleService;
        this.facetConfigService = facetConfigService;
        this.ruleAbTestService = ruleAbTestService;
        this.tenantRepository = tenantRepository;
        this.judgmentSetRepository = judgmentSetRepository;
        this.judgmentRepository = judgmentRepository;
        this.suggestionConfigRepository = suggestionConfigRepository;
        this.jdbc = jdbc;
        this.auditService = auditService;
    }

    /**
     * Filename -> category payload, ready for NR-165 to serialize each value
     * to JSON and zip. LinkedHashMap keeps a stable, readable file order.
     */
    public Map<String, Object> exportAll() {
        String tenantId = TenantContext.getTenantId();
        String projectId = TenantContext.getProjectId();

        Map<String, Object> bundle = new LinkedHashMap<>();
        bundle.put("rules.json", buildRulesExport());
        bundle.put("content-rules.json", buildContentRulesExport());
        bundle.put("facets.json", buildFacetsExport());
        bundle.put("pipeline-config.json", buildPipelineConfigExport());
        bundle.put("approval-settings.json", buildApprovalSettingsExport());
        bundle.put("ab-tests.json", buildAbTestsExport());
        bundle.put("quality-curation.json", buildQualityCurationExport());

        auditService.log("CONFIG_EXPORTED", "ConfigExport", tenantId + "/" + projectId,
                "Exported " + bundle.size() + " config categories");
        return bundle;
    }

    private ExportMeta meta() {
        return new ExportMeta(ExportMeta.CURRENT_SCHEMA_VERSION, Instant.now().toString(),
                TenantContext.getTenantId(), TenantContext.getProjectId());
    }

    RulesExport buildRulesExport() {
        List<RuleExport> rules = merchRuleService.getLiveRules().stream()
                .map(this::toRuleExport)
                .toList();
        return new RulesExport(meta(), rules);
    }

    private RuleExport toRuleExport(MerchRule r) {
        List<TriggerConditionExport> conditions = r.getTriggerConditions() == null ? List.of()
                : r.getTriggerConditions().stream()
                        .map(c -> new TriggerConditionExport(c.getFacetField(), c.getFacetValues()))
                        .toList();
        return new RuleExport(
                r.getId(), r.getType().name(), r.getQuery(),
                r.getBoostField(), r.getBoostValue(), r.getBoostFactor(),
                r.getPinnedIds(), r.getSynonyms(),
                r.getSynonymDirection() == null ? null : r.getSynonymDirection().name(),
                r.getPriority(), r.isRequireQuery(), r.getRedirectUrl(),
                r.getActivateAt(), r.getExpireAt(), conditions);
    }

    ContentRulesExport buildContentRulesExport() {
        // Mirrors the rules LIVE-only decision — only ACTIVE content rules are
        // "config to replicate," not DRAFT/PENDING_REVIEW work in flight.
        List<ContentRuleExport> contentRules = contentRuleService.getAllForExport().stream()
                .filter(r -> r.getStatus() == ContentRule.ContentRuleStatus.ACTIVE)
                .map(this::toContentRuleExport)
                .toList();
        return new ContentRulesExport(meta(), contentRules);
    }

    private ContentRuleExport toContentRuleExport(ContentRule r) {
        List<TriggerConditionExport> conditions = r.getTriggerConditions() == null ? List.of()
                : r.getTriggerConditions().stream()
                        .map(c -> new TriggerConditionExport(c.getFacetField(), c.getFacetValues()))
                        .toList();
        return new ContentRuleExport(
                r.getId(), r.getZone().name(), r.getName(), r.getDescription(), r.getPriority(),
                r.getScheduleStart(), r.getScheduleEnd(), conditions, r.getContentPayload());
    }

    FacetsExport buildFacetsExport() {
        List<FacetExport> facets = facetConfigService.getAllFacets().stream()
                .map(f -> new FacetExport(
                        f.getId(), f.getFieldName(), f.getDisplayLabel(), f.getFacetType().name(),
                        f.isEnabled(), f.isShowCount(), f.getSortOrder(),
                        f.getMaxValues(), f.getRangeMin(), f.getRangeMax(), f.getRangeInterval()))
                .toList();
        return new FacetsExport(meta(), facets);
    }

    PipelineConfigExport buildPipelineConfigExport() {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT stage_name, stage_group, stage_order, enabled
                FROM pipeline_stage_config
                WHERE tenant_id = ? AND project_id = ?
                ORDER BY
                  CASE stage_group
                    WHEN 'PRE_QUERY' THEN 1
                    WHEN 'RULE_APPLICATION' THEN 2
                    WHEN 'POST_QUERY' THEN 3
                  END,
                  stage_order
                """, TenantContext.getTenantId(), TenantContext.getProjectId());

        List<PipelineStageExport> stages = rows.stream()
                .map(row -> new PipelineStageExport(
                        (String) row.get("stage_name"),
                        (String) row.get("stage_group"),
                        ((Number) row.get("stage_order")).intValue(),
                        (Boolean) row.get("enabled")))
                .toList();
        return new PipelineConfigExport(meta(), stages);
    }

    ApprovalSettingsExport buildApprovalSettingsExport() {
        boolean autoPublish = tenantRepository.findById(TenantContext.getTenantId())
                .map(t -> t.isAutoPublishRules())
                .orElse(true);
        return new ApprovalSettingsExport(meta(), autoPublish);
    }

    AbTestsExport buildAbTestsExport() {
        // Only RUNNING tests — impressions/clicks/winnerId are live traffic
        // stats tied to the source environment, not config to replicate.
        List<AbTestExport> abTests = ruleAbTestService.getRunningTests().stream()
                .map(t -> new AbTestExport(t.getId(), t.getQuery(), t.getRuleAId(), t.getRuleBId()))
                .toList();
        return new AbTestsExport(meta(), abTests);
    }

    QualityCurationExport buildQualityCurationExport() {
        String tenantId = TenantContext.getTenantId();
        String projectId = TenantContext.getProjectId();

        List<JudgmentSet> sets = judgmentSetRepository.findByTenantIdAndProjectIdOrderByCreatedAtDesc(tenantId, projectId);
        List<JudgmentSetExport> setExports = sets.stream()
                .map(s -> new JudgmentSetExport(
                        s.getId(), s.getName(), s.getDescription(),
                        judgmentRepository.findBySetId(s.getId()).stream()
                                .map(this::toJudgmentExport)
                                .toList()))
                .toList();

        SuggestionConfigExport suggestionConfig = suggestionConfigRepository
                .findFirstByTenantIdAndProjectId(tenantId, projectId)
                .map(c -> new SuggestionConfigExport(
                        c.getMinCtr(), c.getMaxClickPosition(), c.getMinClicks(),
                        c.getMinImpressions(), c.getLookbackDays(), c.getMaxSuggestions()))
                .orElse(null);

        return new QualityCurationExport(meta(), setExports, suggestionConfig);
    }

    private JudgmentExport toJudgmentExport(Judgment j) {
        return new JudgmentExport(j.getId(), j.getQuery(), j.getProductId(), j.getProductTitle(),
                j.getGrade(), j.getSource(), j.getStatus(), j.getLlmGrade());
    }
}
