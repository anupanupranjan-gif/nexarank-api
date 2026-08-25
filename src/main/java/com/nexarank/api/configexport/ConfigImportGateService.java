// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.configexport;

import com.nexarank.api.service.LlmConfigService;
import com.nexarank.api.service.SearchEngineConfigService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * NR-157/NR-166: hard precondition gate for config import. Per resolved
 * decision #3, import is blocked ENTIRELY — nothing imports, not rules, not
 * content rules, nothing — unless the target environment already has valid
 * engine config AND valid LLM config, set up independently via the normal
 * Configuration screens, before the import runs.
 *
 * "Valid" means testConnection() actually succeeds, not just "a row exists"
 * — a present-but-broken config (wrong host, expired credentials) shouldn't
 * pass this gate either, since the whole point is that the target can
 * actually serve the imported rules once they land.
 */
@Service
public class ConfigImportGateService {

    private final SearchEngineConfigService searchEngineConfigService;
    private final LlmConfigService llmConfigService;

    public ConfigImportGateService(SearchEngineConfigService searchEngineConfigService,
                                    LlmConfigService llmConfigService) {
        this.searchEngineConfigService = searchEngineConfigService;
        this.llmConfigService = llmConfigService;
    }

    public record GateResult(boolean passed, List<String> blockers) {}

    public GateResult check() {
        List<String> blockers = new ArrayList<>();

        var engineTest = searchEngineConfigService.testConnection(null);
        if (!engineTest.success()) {
            blockers.add("Engine config: " + engineTest.message());
        }

        var llmTest = llmConfigService.testConnection(null);
        if (!llmTest.success()) {
            blockers.add("LLM config: " + llmTest.message());
        }

        return new GateResult(blockers.isEmpty(), blockers);
    }
}
