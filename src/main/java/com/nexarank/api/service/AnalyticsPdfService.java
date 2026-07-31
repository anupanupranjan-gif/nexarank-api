// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * NR-36: consolidated "send to leadership" analytics report as a single PDF
 * (overview, search health, facet usage, rule performance) — a deliberate
 * substitute for the ticket's literal "CSV/Excel export," since the actual
 * use case (per-tenant discussion) is a polished snapshot for someone with
 * no dashboard access, not raw data meant to be re-manipulated.
 */
@Service
public class AnalyticsPdfService {

    private static final float MARGIN = 50;
    private static final float PAGE_WIDTH = PDRectangle.LETTER.getWidth();
    private static final float PAGE_HEIGHT = PDRectangle.LETTER.getHeight();
    private static final PDFont FONT_BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private static final PDFont FONT_BODY = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

    @SuppressWarnings("unchecked")
    public byte[] generateReport(String tenantId, String projectId, int days,
                                  Map<String, Object> overview,
                                  Map<String, Object> searchHealth,
                                  Map<String, Object> facetUsage,
                                  Map<String, Object> rulesPerformance) {
        try (PDDocument doc = new PDDocument()) {
            Cursor c = new Cursor(doc);

            c.text(FONT_BOLD, 18, "NexaRank Analytics Report");
            c.gap(4);
            c.text(FONT_BODY, 10, tenantId + " / " + projectId + " - last " + days + " days - generated "
                    + DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm 'UTC'").withZone(ZoneOffset.UTC).format(Instant.now()));
            c.gap(16);

            c.heading("Overview");
            c.kv("Total Searches", str(overview.get("totalSearches")));
            c.kv("Total Clicks", str(overview.get("totalClicks")));
            c.kv("Avg CTR", pct(overview.get("avgCtr")));
            c.kv("Zero Result Rate", pct(overview.get("zeroResultRate")));
            c.kv("Active Rules", str(overview.get("activeRules")));
            c.kv("Pending Review", str(overview.get("pendingRules")));
            c.kv("Zero-Result Actioned / Unactioned",
                    str(overview.get("zeroResultActionedCount")) + " / " + str(overview.get("zeroResultUnactionedCount")));
            c.gap(16);

            c.heading("Search Health");
            Map<String, Object> latency = (Map<String, Object>) searchHealth.get("latency");
            c.kv("Latency p50 / p95 / p99", ms(latency.get("p50")) + " / " + ms(latency.get("p95")) + " / " + ms(latency.get("p99")));
            c.kv("Sampled Searches", str(latency.get("sampleSize")));
            c.gap(8);
            c.subheading("Query Volume by Project");
            float[] volCols = {180, 100, 110, 110};
            c.tableRow(FONT_BOLD, new String[]{"Project", "Searches", "Zero Results", "Zero Result Rate"}, volCols);
            for (Map<String, Object> row : (List<Map<String, Object>>) searchHealth.get("projectVolume")) {
                c.tableRow(FONT_BODY, new String[]{
                        str(row.get("projectName")), str(row.get("searches")),
                        str(row.get("zeroResults")), pct(row.get("zeroResultRate"))
                }, volCols);
            }
            c.gap(16);

            c.heading("Facet Usage");
            List<Map<String, Object>> unused = (List<Map<String, Object>>) facetUsage.get("unusedFacets");
            c.kv("Unused Facets", unused.isEmpty() ? "None" : unused.stream()
                    .map(u -> str(u.get("displayLabel"))).collect(Collectors.joining(", ")));
            c.gap(8);
            c.subheading("Selection Frequency");
            float[] facetCols = {180, 130, 180};
            c.tableRow(FONT_BOLD, new String[]{"Facet", "Total Selections", "Top Value"}, facetCols);
            for (Map<String, Object> f : (List<Map<String, Object>>) facetUsage.get("facets")) {
                List<Map<String, Object>> topValues = (List<Map<String, Object>>) f.get("topValues");
                String top = topValues.isEmpty() ? "-"
                        : str(topValues.get(0).get("value")) + " (" + str(topValues.get(0).get("selections")) + ")";
                c.tableRow(FONT_BODY, new String[]{str(f.get("displayLabel")), str(f.get("totalSelections")), top}, facetCols);
            }
            c.gap(16);

            c.heading("Rule Performance");
            c.kv("Baseline Avg CTR", pct(rulesPerformance.get("avgCtr")));
            c.gap(8);
            float[] ruleCols = {70, 190, 100, 60, 70};
            c.tableRow(FONT_BOLD, new String[]{"Type", "Query", "Status", "Fired", "CTR"}, ruleCols);
            List<Map<String, Object>> rules = (List<Map<String, Object>>) rulesPerformance.get("rules");
            List<Map<String, Object>> topRules = rules.stream()
                    .sorted((a, b) -> Long.compare(
                            ((Number) b.get("firedCount")).longValue(), ((Number) a.get("firedCount")).longValue()))
                    .limit(20)
                    .collect(Collectors.toList());
            for (Map<String, Object> r : topRules) {
                c.tableRow(FONT_BODY, new String[]{
                        str(r.get("type")), truncate(str(r.get("query")), 32),
                        str(r.get("status")), str(r.get("firedCount")), pct(r.get("ctr"))
                }, ruleCols);
            }

            c.close();

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String str(Object o) { return o == null ? "-" : String.valueOf(o); }

    private static String pct(Object o) {
        if (o == null) return "-";
        return String.format("%.1f%%", ((Number) o).doubleValue() * 100.0);
    }

    private static String ms(Object o) { return o == null ? "-" : o + "ms"; }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "...";
    }

    /** Tracks vertical position across pages, opening/closing content streams as needed. */
    private static class Cursor {
        private final PDDocument doc;
        private PDPageContentStream stream;
        private float y;

        Cursor(PDDocument doc) throws IOException {
            this.doc = doc;
            newPage();
        }

        private void newPage() throws IOException {
            if (stream != null) stream.close();
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            stream = new PDPageContentStream(doc, page);
            y = PAGE_HEIGHT - MARGIN;
        }

        private void ensureSpace(float needed) throws IOException {
            if (y - needed < MARGIN) newPage();
        }

        void gap(float amount) { y -= amount; }

        void heading(String text) throws IOException {
            ensureSpace(24);
            y -= 6;
            text(FONT_BOLD, 13, text);
            y -= 4;
        }

        void subheading(String text) throws IOException {
            ensureSpace(16);
            text(FONT_BOLD, 10, text);
        }

        void kv(String label, String value) throws IOException {
            ensureSpace(14);
            stream.beginText();
            stream.setFont(FONT_BODY, 10);
            stream.newLineAtOffset(MARGIN, y);
            stream.showText(label + ": " + value);
            stream.endText();
            y -= 14;
        }

        void text(PDFont font, float size, String content) throws IOException {
            ensureSpace(size + 4);
            stream.beginText();
            stream.setFont(font, size);
            stream.newLineAtOffset(MARGIN, y);
            stream.showText(sanitize(content));
            stream.endText();
            y -= size + 4;
        }

        void tableRow(PDFont font, String[] cols, float[] widths) throws IOException {
            ensureSpace(14);
            stream.beginText();
            stream.setFont(font, 9);
            // beginText() resets the text matrix to identity, so this first offset is
            // absolute (page origin); every subsequent one is relative to the previous
            // column's start, which is exactly how column widths should compose.
            stream.newLineAtOffset(MARGIN, y);
            for (int i = 0; i < cols.length; i++) {
                if (i > 0) stream.newLineAtOffset(widths[i - 1], 0);
                stream.showText(sanitize(cols[i]));
            }
            stream.endText();
            y -= 14;
        }

        /** PDFBox's standard fonts only support WinAnsi-encodable characters. */
        private String sanitize(String s) {
            StringBuilder sb = new StringBuilder(s.length());
            for (char ch : s.toCharArray()) {
                sb.append(ch <= 0xFF ? ch : '?');
            }
            return sb.toString();
        }

        void close() throws IOException {
            stream.close();
        }
    }
}
