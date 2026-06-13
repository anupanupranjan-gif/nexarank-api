// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.service;

import com.nexarank.api.model.Stopword;
import com.nexarank.api.repository.StopwordRepository;
import com.nexarank.api.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class StopwordService {

    private static final Logger log = LoggerFactory.getLogger(StopwordService.class);

    private final StopwordRepository repository;

    // In-memory cache keyed by "tenantId:projectId"
    // Invalidated on any add/delete. Good enough for dev-scale;
    // replace with Caffeine or Redis for production.
    private final Map<String, Set<String>> cache = new ConcurrentHashMap<>();

    public StopwordService(StopwordRepository repository) {
        this.repository = repository;
    }

    /**
     * Get stopwords for current tenant/project as a Set for O(1) lookup.
     * Results are cached in memory — invalidated on any mutation.
     */
    public Set<String> getStopwords() {
        String key = cacheKey();
        return cache.computeIfAbsent(key, k -> {
            List<String> words = repository.findWordsByTenantIdAndProjectId(
                TenantContext.getTenantId(), TenantContext.getProjectId());
            log.debug("Loaded {} stopwords for {}", words.size(), key);
            return Set.copyOf(words.stream()
                .map(String::toLowerCase)
                .collect(Collectors.toSet()));
        });
    }

    public List<Stopword> getAll() {
        return repository.findByTenantIdAndProjectId(
            TenantContext.getTenantId(), TenantContext.getProjectId());
    }

    public Stopword add(String word) {
        String tenantId  = TenantContext.getTenantId();
        String projectId = TenantContext.getProjectId();
        String normalized = word.trim().toLowerCase();

        // Idempotent — return existing if already present
        return repository.findByTenantIdAndProjectIdAndWord(tenantId, projectId, normalized)
            .orElseGet(() -> {
                Stopword sw = new Stopword();
                sw.setTenantId(tenantId);
                sw.setProjectId(projectId);
                sw.setWord(normalized);
                sw.setCreatedBy(currentUser());
                Stopword saved = repository.save(sw);
                invalidateCache();
                log.info("Stopword added: '{}' for {}/{}", normalized, tenantId, projectId);
                return saved;
            });
    }

    @Transactional
    public void delete(String word) {
        repository.deleteByTenantIdAndProjectIdAndWord(
            TenantContext.getTenantId(), TenantContext.getProjectId(),
            word.trim().toLowerCase());
        invalidateCache();
        log.info("Stopword deleted: '{}'", word);
    }

    public void addBulk(List<String> words) {
        words.forEach(this::add);
    }

    private void invalidateCache() {
        cache.remove(cacheKey());
    }

    private String cacheKey() {
        return TenantContext.getTenantId() + ":" + TenantContext.getProjectId();
    }

    private String currentUser() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "system";
    }
}
