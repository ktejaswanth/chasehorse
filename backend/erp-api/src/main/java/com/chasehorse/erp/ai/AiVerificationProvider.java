package com.chasehorse.erp.ai;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Extension point for an AI image/consistency check.
 *
 * IMPORTANT: results are ADVISORY. Nothing in the system lets an AI result approve a task,
 * release a reward or skip human review (enforced in the database: only decide_task, called by
 * a human reviewer, changes approval state). No provider ships with this codebase; until a real
 * one is implemented and configured, submissions are marked "manual review required".
 */
public interface AiVerificationProvider {

    boolean isConfigured();

    /** Short provider name stored with each result, e.g. "acme-vision". */
    String providerName();

    /** Must either return a real outcome or throw; never fabricate a result. */
    AiOutcome verify(AiRequest request) throws AiProviderException;

    record AiRequest(UUID taskId, int submissionNo, List<String> evidenceStoragePaths, List<String> checklistLabels) {
    }

    /** recommendation: looks_consistent | needs_review | issues_found */
    record AiOutcome(String recommendation, BigDecimal confidence, List<String> reasonCodes, String summary, String modelVersion) {
    }

    class AiProviderException extends Exception {
        public AiProviderException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
