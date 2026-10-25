package com.chasehorse.erp.integration;

/**
 * A future connection to an outside system (TMS, GST, Vahan, payments). None is implemented: every
 * adapter below reports NOT_CONFIGURED and refuses to run, so the system can never pretend that a
 * verification, filing or payout happened. Real adapters must implement the typed interface, report
 * isConfigured() honestly and be added behind explicit credentials and a documented business approval.
 */
public interface ExternalIntegration {
    String key();

    String displayName();

    boolean isConfigured();

    default String status() {
        return isConfigured() ? "configured" : "not_configured";
    }
}
