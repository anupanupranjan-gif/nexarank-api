// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.adapter;

import com.nexarank.api.model.LlmConfig;
import com.nexarank.api.port.LlmPort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Selects the correct LlmPort adapter based on config.provider.
 * Spring injects all LlmPort implementations automatically.
 * Adding a new provider: implement LlmPort, annotate @Component — done.
 */
@Component
public class LlmAdapterFactory {

    private final Map<LlmConfig.LlmProvider, LlmPort> adapters;

    public LlmAdapterFactory(List<LlmPort> adapterList) {
        this.adapters = adapterList.stream()
            .collect(Collectors.toMap(LlmPort::supportedProvider, a -> a));
    }

    public LlmPort getAdapter(LlmConfig config) {
        if (config == null || config.getProvider() == null) {
            throw new IllegalArgumentException("LlmConfig must have a provider");
        }
        LlmPort adapter = adapters.get(config.getProvider());
        if (adapter == null) {
            throw new UnsupportedOperationException(
                "No adapter for LLM provider: " + config.getProvider() +
                ". Supported: " + adapters.keySet());
        }
        return adapter;
    }

    public boolean supportsProvider(LlmConfig.LlmProvider provider) {
        return adapters.containsKey(provider);
    }
}
