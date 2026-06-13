// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.pipeline.stage;

import com.nexarank.api.model.EnrichedQuery;
import com.nexarank.api.pipeline.PipelineContext;
import com.nexarank.api.pipeline.PipelineStage;
import com.nexarank.api.repository.ClickEventRepository;
import com.nexarank.api.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

/**
 * POST_QUERY stage: computes personalized product boost IDs from session click history.
 *
 * Looks up what products the current sessionId has clicked in the last 30 days,
 * ranked by click frequency. Adds the top N product IDs to EnrichedQuery.personalizedBoostIds.
 *
 * The client applies these as additional boosts in their search query —
 * e.g. ES PinnedQuery or FunctionScore boost for these product IDs.
 *
 * Skipped if:
 * - No sessionId in the request
 * - No click history found for the session
 * - Match-all query (browsing, not searching)
 */
@Component
public class PersonalizationStage implements PipelineStage {

    private static final Logger log = LoggerFactory.getLogger(PersonalizationStage.class);
    private static final int MAX_BOOST_IDS = 10;
    private static final int LOOKBACK_DAYS = 30;

    private final ClickEventRepository clickEventRepository;

    public PersonalizationStage(ClickEventRepository clickEventRepository) {
        this.clickEventRepository = clickEventRepository;
    }

    @Override public String name()       { return "PERSONALIZATION"; }
    @Override public StageGroup group()  { return StageGroup.POST_QUERY; }
    @Override public int defaultOrder()  { return 10; }

    @Override
    public void execute(PipelineContext context) {
        long start = System.currentTimeMillis();

        if (context.isMatchAll()) {
            context.addTrace(name(), "session=" + context.getSessionId(),
                "skipped (match-all)", 0, true);
            return;
        }

        if (context.getSessionId() == null || context.getSessionId().isBlank()) {
            context.addTrace(name(), "no sessionId", "skipped", 0, true);
            return;
        }

        try {
            String tenantId  = TenantContext.getTenantId();
            String projectId = TenantContext.getProjectId();
            Instant since    = Instant.now().minus(LOOKBACK_DAYS, ChronoUnit.DAYS);

            // Get products clicked by this session, ordered by click count desc
            List<Object[]> sessionClicks = clickEventRepository
                .findTopClickedProductsBySession(tenantId, projectId,
                    context.getSessionId(), since);

            if (sessionClicks.isEmpty()) {
                context.addTrace(name(), "session=" + context.getSessionId(),
                    "no click history", System.currentTimeMillis() - start, true);
                return;
            }

            List<String> boostIds = sessionClicks.stream()
                .limit(MAX_BOOST_IDS)
                .map(row -> (String) row[0])  // row[0] = product_id
                .collect(Collectors.toList());

            // Write to EnrichedQuery — client applies these as boosts
            EnrichedQuery enriched = context.getEnrichedQuery();
            if (enriched != null) {
                enriched.setPersonalizedBoostIds(boostIds);
            }

            long took = System.currentTimeMillis() - start;
            log.info("PERSONALIZATION session={} boostIds={} took={}ms",
                context.getSessionId(), boostIds.size(), took);
            context.addTrace(name(), "session=" + context.getSessionId(),
                "boostIds=" + boostIds.size(), took, false);

        } catch (Exception e) {
            long took = System.currentTimeMillis() - start;
            log.warn("PERSONALIZATION failed: {} — skipping", e.getMessage());
            context.addTrace(name(), "session=" + context.getSessionId(),
                "error: " + e.getMessage(), took, false);
        }
    }
}
