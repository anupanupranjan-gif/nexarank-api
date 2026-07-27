// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.controller;

import com.nexarank.api.model.ContentRule;
import com.nexarank.api.model.ContentZone;
import com.nexarank.api.service.ContentRuleService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * NR-83: the customer-facing Experience Manager endpoint. search-ui/AvinoShop
 * call this on page load with the zones present on that page plus page
 * context, and get back the winning content payload per zone (or nothing,
 * if no ACTIVE rule matches — client falls back to its own default content).
 */
@RestController
@RequestMapping("/api/v1/content")
public class ContentEnrichController {

    private final ContentRuleService service;

    public ContentEnrichController(ContentRuleService service) {
        this.service = service;
    }

    /**
     * POST /api/v1/content/enrich
     *
     * Request:
     * {
     *   "zones": ["HERO_BANNER", "ANNOUNCEMENT_BAR"],
     *   "context": { "category": "...", "query": "...", "customerSegment": "...", "deviceType": "..." }
     * }
     *
     * Response:
     * { "zones": { "HERO_BANNER": { contentRuleId, headline, subheadline, imageUrl,
     *                                ctaText, ctaLink, backgroundColor, textColor }, ... } }
     */
    @PostMapping("/enrich")
    public ResponseEntity<Map<String, Object>> enrich(@RequestBody Map<String, Object> request) {
        List<ContentZone> zones = parseZones(request.get("zones"));
        Map<String, String> context = parseContext(request.get("context"));

        if (zones.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        Map<ContentZone, ContentRule> winners = service.resolveZones(zones, context);

        Map<String, Object> zoneResults = new LinkedHashMap<>();
        winners.forEach((zone, rule) -> zoneResults.put(zone.name(), toPayload(rule)));

        return ResponseEntity.ok(Map.of("zones", zoneResults));
    }

    private Map<String, Object> toPayload(ContentRule rule) {
        Map<String, String> content = rule.getContentPayload() != null ? rule.getContentPayload() : Map.of();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("contentRuleId", rule.getId());
        payload.put("headline", content.get("headline"));
        payload.put("subheadline", content.get("subheadline"));
        payload.put("imageUrl", content.get("image_url"));
        payload.put("ctaText", content.get("cta_text"));
        payload.put("ctaLink", content.get("cta_link"));
        payload.put("backgroundColor", content.get("background_color"));
        payload.put("textColor", content.get("text_color"));
        // PROMO_GRID/FEATURED_PRODUCTS-style zones carry extra keys the fixed
        // fields above don't cover — pass the raw payload through too.
        payload.put("raw", content);
        return payload;
    }

    @SuppressWarnings("unchecked")
    private List<ContentZone> parseZones(Object raw) {
        if (!(raw instanceof List<?> list)) return List.of();
        return list.stream()
                .map(Object::toString)
                .map(s -> {
                    try { return ContentZone.valueOf(s); }
                    catch (IllegalArgumentException e) { return null; }
                })
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private Map<String, String> parseContext(Object raw) {
        if (!(raw instanceof Map<?, ?> map)) return Map.of();
        Map<String, String> context = new LinkedHashMap<>();
        map.forEach((k, v) -> {
            if (k != null && v != null) context.put(k.toString(), v.toString());
        });
        return context;
    }
}
