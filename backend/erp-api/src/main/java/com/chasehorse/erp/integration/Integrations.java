package com.chasehorse.erp.integration;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.chasehorse.erp.common.ApiException;

/** Typed extension points + their default "not configured" adapters. */
public final class Integrations {
    private Integrations() {
    }

    public interface TmsClient extends ExternalIntegration {
        void pushTaskStatus(UUID taskId, String status);
    }

    public interface GstClient extends ExternalIntegration {
        GstinResult verifyGstin(String gstin);

        record GstinResult(String gstin, boolean valid, String legalName) {
        }
    }

    public interface VahanClient extends ExternalIntegration {
        VehicleResult lookupVehicle(String registrationNumber);

        record VehicleResult(String registrationNumber, String ownerName, String status) {
        }
    }

    public interface PaymentGateway extends ExternalIntegration {
        /** Must be idempotent on idempotencyKey. */
        PayoutResult createPayout(UUID userId, long amountPaise, String idempotencyKey);

        record PayoutResult(String externalReference, String status) {
        }
    }

    private static ApiException off(String what) {
        return ApiException.notConfigured(what + " integration is not configured.");
    }

    @Component
    static class NoTms implements TmsClient {
        public String key() { return "tms"; }
        public String displayName() { return "Transport Management System (TMS)"; }
        public boolean isConfigured() { return false; }
        public void pushTaskStatus(UUID taskId, String status) { throw off("TMS"); }
    }

    @Component
    static class NoGst implements GstClient {
        public String key() { return "gst"; }
        public String displayName() { return "GST verification"; }
        public boolean isConfigured() { return false; }
        public GstinResult verifyGstin(String gstin) { throw off("GST"); }
    }

    @Component
    static class NoVahan implements VahanClient {
        public String key() { return "vahan"; }
        public String displayName() { return "Vahan vehicle lookup"; }
        public boolean isConfigured() { return false; }
        public VehicleResult lookupVehicle(String registrationNumber) { throw off("Vahan"); }
    }

    @Component
    static class NoPayments implements PaymentGateway {
        public String key() { return "payments"; }
        public String displayName() { return "Payment gateway / payouts"; }
        public boolean isConfigured() { return false; }
        public PayoutResult createPayout(UUID userId, long amountPaise, String idempotencyKey) { throw off("Payment"); }
    }
}
