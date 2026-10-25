package com.chasehorse.erp.ai;

import org.springframework.stereotype.Component;

/** Default: no AI provider is connected. It never returns a result. */
@Component
public class NotConfiguredAiProvider implements AiVerificationProvider {
    @Override
    public boolean isConfigured() {
        return false;
    }

    @Override
    public String providerName() {
        return "none";
    }

    @Override
    public AiOutcome verify(AiRequest request) {
        throw new UnsupportedOperationException("No AI provider is configured.");
    }
}
