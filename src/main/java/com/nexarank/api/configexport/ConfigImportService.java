// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.configexport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexarank.api.configexport.dto.*;
import com.nexarank.api.model.Judgment;
import com.nexarank.api.model.JudgmentSet;
import com.nexarank.api.model.SuggestionConfig;
import com.nexarank.api.repository.JudgmentRepository;
import com.nexarank.api.repository.JudgmentSetRepository;
import com.nexarank.api.repository.TenantRepository;
import com.nexarank.api.security.TenantContext;
import com.nexarank.api.service.ContentRuleService;
import com.nexarank.api.service.FacetConfigService;
import com.nexarank.api.service.MerchRuleService;
import com.nexarank.api.service.RuleAbTestService;
import com.nexarank.api.service.SuggestionConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * NR-157/NR-167: applies an uploaded config export bundle (NR-164's format)
 * to the current tenant+project. Only files actually present in the upload
 * are applied — a partial set is valid, matching the per-category-file
 * export decision. Every category upserts by the id preserved in the
 * export (see MerchRuleService.importRule's javadoc for why a same-id
 * match is only honored within the current tenant+project, never across).
 */
@Service
public class ConfigImportService {

    private static final Logger log = LoggerFactory.getLogger(ConfigImportService.class);

    private final ConfigImportGateService gateService;
    private final ObjectMapper objectMapper;
    private final MerchRuleService merchRuleService;
    private final ContentRuleService contentRuleService;
    private final FacetConfigService facetConfigService;
    private final RuleAbTestService ruleAbTestService;
    private final SuggestionConfigService suggestionConfigService;
    private final TenantRepository tenantRepository;
    private final JudgmentSetRepository judgmentSetRepository;
    private final JudgmentRepository judgmentRepository;

    public ConfigImportService(ConfigImportGateService gateService,
                                ObjectMapper objectMapper,
                                MerchRuleService merchRuleService,
                                ContentRuleService contentRuleService,
                                FacetConfigService facetConfigService,
                                RuleAbTestService ruleAbTestService,
                                SuggestionConfigService suggestionConfigService,
                                TenantRepository tenantRepository,
                                JudgmentSetRepository judgmentSetRepository,
                                JudgmentRepository judgmentRepository) {
        this.gateService = gateService;
        this.objectMapper = objectMapper;
        this.merchRuleService = merchRuleService;
        this.contentRuleService = contentRuleService;
        this.facetConfigService = facetConfigService;
        this.ruleAbTestService = ruleAbTestService;
        this.suggestionConfigService = suggestionConfigService;
        this.tenantRepository = tenantRepository;
        this.judgmentSetRepository = judgmentSetRepository;
        this.judgmentRepository = judgmentRepository;
    }

    /** Thrown when the NR-166 precondition gate hasn't passed — caller should surface as 412, not 400/500. */
    public static class GateNotPassedException extends RuntimeException {
        public final List<String> blockers;
        public GateNotPassedException(List<String> blockers) {
            super("Import blocked: " + String.join("; ", blockers));
            this.blockers = blockers;
        }
    }

    public ImportSummary importBundle(Map<String, byte[]> files) {
        ConfigImportGateService.GateResult gate = gateService.check();
        if (!gate.passed()) {
            throw new GateNotPassedException(gate.blockers());
        }

        // Parse + validate schema version for every present file BEFORE
        // applying anything, so a bad file in the bundle can't leave a
        // partially-applied import behind.
        RulesExport rules = parse(files, "rules.json", RulesExport.class, RulesExport::meta);
        ContentRulesExport contentRules = parse(files, "content-rules.json", ContentRulesExport.class, ContentRulesExport::meta);
        FacetsExport facets = parse(files, "facets.json", FacetsExport.class, FacetsExport::meta);
        PipelineConfigExport pipelineConfig = parse(files, "pipeline-config.json", PipelineConfigExport.class, PipelineConfigExport::meta);
        ApprovalSettingsExport approvalSettings = parse(files, "approval-settings.json", ApprovalSettingsExport.class, ApprovalSettingsExport::meta);
        AbTestsExport abTests = parse(files, "ab-tests.json", AbTestsExport.class, AbTestsExport::meta);
        QualityCurationExport qualityCuration = parse(files, "quality-curation.json", QualityCurationExport.class, QualityCurationExport::meta);

        Map<String, Integer> imported = new LinkedHashMap<>();
        Map<String, Integer> collisions = new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>();

        // rules.json first — ab-tests.json needs its id remap.
        Map<String, String> ruleIdRemap = new HashMap<>();
        if (rules != null) {
            int collisionCount = 0;
            for (RuleExport dto : rules.rules()) {
                MerchRuleService.ImportResult result = merchRuleService.importRule(dto);
                ruleIdRemap.put(dto.id(), result.rule().getId());
                if (result.idCollisionResolved()) collisionCount++;
            }
            imported.put("rules", rules.rules().size());
            if (collisionCount > 0) collisions.put("rules", collisionCount);
        }

        if (contentRules != null) {
            int collisionCount = 0;
            for (ContentRuleExport dto : contentRules.contentRules()) {
                if (contentRuleService.importContentRule(dto).idCollisionResolved()) collisionCount++;
            }
            imported.put("contentRules", contentRules.contentRules().size());
            if (collisionCount > 0) collisions.put("contentRules", collisionCount);
        }

        if (facets != null) {
            int collisionCount = 0;
            for (FacetExport dto : facets.facets()) {
                if (facetConfigService.importFacet(dto).idCollisionResolved()) collisionCount++;
            }
            imported.put("facets", facets.facets().size());
            if (collisionCount > 0) collisions.put("facets", collisionCount);
        }

        if (pipelineConfig != null) {
            // Pipeline stage config has no natural "import" — it's one row
            // per (tenant, project, stage_name) — deliberately not applied
            // for v1: silently changing which of the 10 stages run on
            // import is a bigger surprise than the other categories (it
            // changes query BEHAVIOR platform-wide, not just adds data),
            // and there's no existing service method to reuse safely.
            // Surfaced as a warning so it isn't silently dropped either.
            warnings.add("pipeline-config.json was present but is not applied by import (view/apply manually via Pipeline Editor)");
        }

        if (approvalSettings != null) {
            String tenantId = TenantContext.getTenantId();
            tenantRepository.findById(tenantId).ifPresent(t -> {
                t.setAutoPublishRules(approvalSettings.autoPublishRules());
                tenantRepository.save(t);
            });
            imported.put("approvalSettings", 1);
        }

        if (abTests != null) {
            int collisionCount = 0;
            for (AbTestExport dto : abTests.abTests()) {
                String ruleA = ruleIdRemap.get(dto.ruleAId());
                String ruleB = ruleIdRemap.get(dto.ruleBId());
                if (ruleA == null || ruleB == null) {
                    warnings.add("Skipped A/B test for query '" + dto.query()
                            + "' — referenced rule not present in rules.json");
                    continue;
                }
                if (ruleAbTestService.importAbTest(dto, ruleA, ruleB).idCollisionResolved()) collisionCount++;
            }
            imported.put("abTests", abTests.abTests().size());
            if (collisionCount > 0) collisions.put("abTests", collisionCount);
        }

        if (qualityCuration != null) {
            int setCount = importQualityCuration(qualityCuration);
            imported.put("judgmentSets", setCount);
            if (qualityCuration.suggestionConfig() != null) {
                SuggestionConfig config = new SuggestionConfig();
                config.setMinCtr(qualityCuration.suggestionConfig().minCtr());
                config.setMaxClickPosition(qualityCuration.suggestionConfig().maxClickPosition());
                config.setMinClicks(qualityCuration.suggestionConfig().minClicks());
                config.setMinImpressions(qualityCuration.suggestionConfig().minImpressions());
                config.setLookbackDays(qualityCuration.suggestionConfig().lookbackDays());
                config.setMaxSuggestions(qualityCuration.suggestionConfig().maxSuggestions());
                suggestionConfigService.saveConfig(config);
                imported.merge("suggestionConfig", 1, Integer::sum);
            }
        }

        log.info("CONFIG_IMPORTED imported={} collisions={} warnings={}", imported, collisions, warnings.size());
        return new ImportSummary(imported, collisions, warnings);
    }

    private int importQualityCuration(QualityCurationExport dto) {
        String tenantId = TenantContext.getTenantId();
        String projectId = TenantContext.getProjectId();

        for (JudgmentSetExport setDto : dto.judgmentSets()) {
            Optional<JudgmentSet> existingAnywhere = judgmentSetRepository.findById(setDto.id());
            boolean idCollision = existingAnywhere.isPresent() &&
                    !(tenantId.equals(existingAnywhere.get().getTenantId())
                            && projectId.equals(existingAnywhere.get().getProjectId()));

            JudgmentSet set;
            if (existingAnywhere.isPresent() && !idCollision) {
                set = existingAnywhere.get();
            } else {
                set = new JudgmentSet();
                set.setId(idCollision ? UUID.randomUUID().toString() : setDto.id());
                set.setTenantId(tenantId);
                set.setProjectId(projectId);
                set.setCreatedAt(Instant.now());
                set.setCreatedBy("import");
            }
            set.setName(setDto.name());
            set.setDescription(setDto.description());
            set.setUpdatedAt(Instant.now());
            JudgmentSet savedSet = judgmentSetRepository.save(set);

            for (JudgmentExport judgmentDto : setDto.judgments()) {
                Optional<Judgment> existingJudgment = judgmentRepository.findById(judgmentDto.id());
                Judgment judgment = existingJudgment
                        .filter(j -> j.getSetId().equals(savedSet.getId()))
                        .orElseGet(Judgment::new);
                if (judgment.getId() == null) {
                    judgment.setId(existingJudgment.isPresent() ? UUID.randomUUID().toString() : judgmentDto.id());
                }
                judgment.setSetId(savedSet.getId());
                judgment.setQuery(judgmentDto.query());
                judgment.setProductId(judgmentDto.productId());
                judgment.setProductTitle(judgmentDto.productTitle());
                judgment.setGrade(judgmentDto.grade());
                judgment.setSource(judgmentDto.source());
                judgment.setStatus(judgmentDto.status());
                judgment.setLlmGrade(judgmentDto.llmGrade());
                judgment.setJudgedBy("import");
                judgment.setJudgedAt(Instant.now());
                judgmentRepository.save(judgment);
            }
        }
        return dto.judgmentSets().size();
    }

    @SuppressWarnings("unchecked")
    private <T> T parse(Map<String, byte[]> files, String filename, Class<T> type,
                         java.util.function.Function<T, ExportMeta> metaExtractor) {
        byte[] bytes = files.get(filename);
        if (bytes == null) return null;
        T value;
        try {
            value = objectMapper.readValue(bytes, type);
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not parse " + filename + ": " + e.getMessage());
        }
        ExportMeta meta = metaExtractor.apply(value);
        if (meta == null || meta.schemaVersion() != ExportMeta.CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException(filename + " has unrecognized schemaVersion "
                    + (meta == null ? "null" : meta.schemaVersion())
                    + " (expected " + ExportMeta.CURRENT_SCHEMA_VERSION + ")");
        }
        return value;
    }
}
