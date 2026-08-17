// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexarank.api.model.MerchRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * NR-70 Tier 1: structural field-level diff between two MerchRule states.
 *
 * Deliberately a plain key-by-key comparison of the before/after JSON — not a
 * raw JSON dump and not an LLM-generated summary. The output is a list of
 * {field, oldValue, newValue} so the UI can render "priority: 5 -> 10", which
 * is what actually gives a merchandiser a feel for what changed at a glance.
 */
@Service
public class AuditDiffService {

    private static final Logger log = LoggerFactory.getLogger(AuditDiffService.class);

    /**
     * Bookkeeping fields that change on every save and would bury the real edit
     * in noise. updatedAt in particular changes on literally every mutation.
     */
    private static final Set<String> IGNORED_FIELDS = Set.of(
            "id", "tenantId", "projectId", "createdAt", "updatedAt",
            // transient/serialized duplicates — the JSON-backed column and its
            // deserialized counterpart both appear, and would each report the
            // same edit twice.
            "synonymsJson", "pinnedIdsJson", "triggerConditions",
            // MerchRule exposes reviewComment and rejectionComment as two
            // accessor pairs over the SAME field, so both serialize and the
            // diff would report one edit twice. Keep the friendlier label.
            "rejectionComment"
    );

    /** Human-friendly labels; anything unlisted falls back to the raw field name. */
    private static final Map<String, String> FIELD_LABELS = Map.ofEntries(
            Map.entry("type", "Type"),
            Map.entry("query", "Query"),
            Map.entry("priority", "Priority"),
            Map.entry("boostField", "Boost field"),
            Map.entry("boostValue", "Boost value"),
            Map.entry("boostFactor", "Boost factor"),
            Map.entry("buryField", "Bury field"),
            Map.entry("buryValue", "Bury value"),
            Map.entry("redirectUrl", "Redirect URL"),
            Map.entry("synonyms", "Synonyms"),
            Map.entry("pinnedIds", "Pinned products"),
            Map.entry("status", "Status"),
            Map.entry("enabled", "Enabled"),
            Map.entry("requireQuery", "Require query match"),
            Map.entry("activateAt", "Activate at"),
            Map.entry("expireAt", "Expire at"),
            Map.entry("reviewComment", "Review comment"),
            Map.entry("approvedBy", "Approved by"),
            Map.entry("submittedBy", "Submitted by"),
            Map.entry("sourceZeroResultQuery", "Source zero-result query"),
            Map.entry("firedCount", "Fired count"),
            Map.entry("lastFiredAt", "Last fired at")
    );

    private final ObjectMapper objectMapper;

    public AuditDiffService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * A single changed field. Values are rendered as display strings rather than
     * typed JSON — the audit log is read by humans, and a null becomes an
     * explicit em-dash-free "(empty)" so "set for the first time" is legible.
     */
    public record FieldChange(String field, String label, String oldValue, String newValue) {}

    /**
     * @return the changed fields between two rule states, empty if nothing
     *         meaningful changed. Never throws — an audit write must not be able
     *         to fail the business operation it is recording.
     */
    public List<FieldChange> diff(MerchRule before, MerchRule after) {
        List<FieldChange> changes = new ArrayList<>();
        if (before == null || after == null) return changes;
        try {
            JsonNode b = objectMapper.valueToTree(before);
            JsonNode a = objectMapper.valueToTree(after);

            // Union of both sides so a field cleared to null is still reported.
            java.util.LinkedHashSet<String> fields = new java.util.LinkedHashSet<>();
            for (Iterator<String> it = b.fieldNames(); it.hasNext(); ) fields.add(it.next());
            for (Iterator<String> it = a.fieldNames(); it.hasNext(); ) fields.add(it.next());

            for (String field : fields) {
                if (IGNORED_FIELDS.contains(field)) continue;
                String oldVal = render(b.get(field));
                String newVal = render(a.get(field));
                if (!oldVal.equals(newVal)) {
                    changes.add(new FieldChange(
                            field, FIELD_LABELS.getOrDefault(field, field), oldVal, newVal));
                }
            }
        } catch (Exception e) {
            log.warn("Failed to compute audit diff: {}", e.getMessage());
        }
        return changes;
    }

    /**
     * Detached deep copy of a rule's current state.
     *
     * Necessary because JPA entities are mutated in place before save — without
     * copying first, "before" and "after" would be the same object reference and
     * every diff would come back empty.
     */
    public MerchRule copyOf(MerchRule rule) {
        if (rule == null) return null;
        try {
            return objectMapper.readValue(objectMapper.writeValueAsString(rule), MerchRule.class);
        } catch (Exception e) {
            log.warn("Failed to copy rule state for audit diff: {}", e.getMessage());
            return null;
        }
    }

    /** Serialize a diff for the field_diff column. Returns null for an empty diff. */
    public String toJson(List<FieldChange> changes) {
        if (changes == null || changes.isEmpty()) return null;
        try {
            List<Map<String, String>> out = new ArrayList<>();
            for (FieldChange c : changes) {
                Map<String, String> m = new LinkedHashMap<>();
                m.put("field", c.field());
                m.put("label", c.label());
                m.put("oldValue", c.oldValue());
                m.put("newValue", c.newValue());
                out.add(m);
            }
            return objectMapper.writeValueAsString(out);
        } catch (Exception e) {
            log.warn("Failed to serialize audit diff: {}", e.getMessage());
            return null;
        }
    }

    /**
     * One-line summary for CSV export and log lines, e.g.
     * "priority: 5 -> 10; enabled: false -> true".
     */
    public String toSummary(List<FieldChange> changes) {
        if (changes == null || changes.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (FieldChange c : changes) {
            if (sb.length() > 0) sb.append("; ");
            sb.append(c.label()).append(": ").append(c.oldValue()).append(" -> ").append(c.newValue());
        }
        return sb.toString();
    }

    private String render(JsonNode node) {
        if (node == null || node.isNull()) return "(empty)";
        if (node.isTextual()) {
            String t = node.asText();
            return t.isBlank() ? "(empty)" : t;
        }
        if (node.isArray()) {
            if (node.isEmpty()) return "(empty)";
            List<String> parts = new ArrayList<>();
            node.forEach(n -> parts.add(n.isTextual() ? n.asText() : n.toString()));
            return String.join(", ", parts);
        }
        return node.toString();
    }
}
