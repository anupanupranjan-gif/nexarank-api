// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * A cheap, shared invalidation signal for search-api's search-result cache.
 * search-api's cache key folds this counter in (see NR-100/facet cache fix for the
 * same pattern applied to facet config) so a rule mutation invalidates any cached
 * query immediately instead of waiting out search-api's cache TTL — without search-api
 * having to call the expensive /rules/enrich pipeline (LLM rewrite, rule matching) on
 * every request just to detect staleness.
 */
@Service
public class RulesCacheVersionService {

    private final StringRedisTemplate redis;

    public RulesCacheVersionService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void bump(String tenantId, String projectId) {
        redis.opsForValue().increment("rules:version:" + tenantId + ":" + projectId);
    }
}
