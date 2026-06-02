// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.adapter;

import com.nexarank.api.model.SearchEngineConfig;
import com.nexarank.api.port.SearchEnginePort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Selects the correct SearchEnginePort adapter based on config.engineType.
 * Spring injects all SearchEnginePort implementations automatically.
 * Adding a new engine: implement SearchEnginePort, annotate @Component — done.
 */
@Component
public class SearchEngineAdapterFactory {

    private final Map<SearchEngineConfig.EngineType, SearchEnginePort> adapters;

    public SearchEngineAdapterFactory(List<SearchEnginePort> adapterList) {
        this.adapters = adapterList.stream()
            .collect(Collectors.toMap(
                SearchEnginePort::supportedEngine,
                a -> a
            ));
    }

    public SearchEnginePort getAdapter(SearchEngineConfig config) {
        if (config == null || config.getEngineType() == null) {
            throw new IllegalArgumentException("SearchEngineConfig must have an engineType");
        }
        SearchEnginePort adapter = adapters.get(config.getEngineType());
        if (adapter == null) {
            throw new UnsupportedOperationException(
                "No adapter found for engine type: " + config.getEngineType() +
                ". Supported: " + adapters.keySet()
            );
        }
        return adapter;
    }

    public boolean supportsEngine(SearchEngineConfig.EngineType engineType) {
        return adapters.containsKey(engineType);
    }
}
