// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.model;

import java.util.List;

/**
 * Represents a field in the customer's search index.
 * Returned by SearchEnginePort.getFields() for use in
 * rule configuration UI (field dropdowns, facet setup).
 */
public class SearchField {

    private String name;
    private String type;        // keyword, text, integer, float, date, boolean
    private boolean indexed;
    private boolean stored;
    private boolean facetable;  // can be used as a facet
    private boolean sortable;
    private List<String> sampleValues; // first few distinct values

    public SearchField() {}

    public SearchField(String name, String type, boolean indexed,
                       boolean stored, boolean facetable) {
        this.name = name;
        this.type = type;
        this.indexed = indexed;
        this.stored = stored;
        this.facetable = facetable;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public boolean isIndexed() { return indexed; }
    public void setIndexed(boolean indexed) { this.indexed = indexed; }

    public boolean isStored() { return stored; }
    public void setStored(boolean stored) { this.stored = stored; }

    public boolean isFacetable() { return facetable; }
    public void setFacetable(boolean facetable) { this.facetable = facetable; }

    public boolean isSortable() { return sortable; }
    public void setSortable(boolean sortable) { this.sortable = sortable; }

    public List<String> getSampleValues() { return sampleValues; }
    public void setSampleValues(List<String> values) { this.sampleValues = values; }
}
