package com.chasehorse.erp.wallet;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class WalletDtos {
    private WalletDtos() {
    }

    /**
     * INTERNAL reward ledger. payoutsEnabled is always false: no payment provider is integrated, nothing can be
     * withdrawn, and a balance is not money in a bank account.
     */
    public record WalletSummary(UUID userId, String displayName, String currency, long balancePaise, long pendingPaise,
                                boolean payoutsEnabled) {
    }

    public record LedgerEntry(UUID id, long seq, String entryType, long amountPaise, long balanceAfterPaise, UUID taskId,
                              UUID reversesEntryId, String memo, OffsetDateTime createdAt) {
    }

    public record MyWallet(WalletSummary wallet, List<LedgerEntry> recent) {
    }

    public record SettlementResult(UUID taskId, UUID ledgerEntryId) {
    }

    public record ReversalRequest(@NotBlank @Size(min = 3, max = 500) String reason) {
    }

    public record ReversalResult(UUID originalEntryId, UUID reversalEntryId) {
    }

    public record Discrepancy(UUID userId, long cachedPaise, long ledgerPaise) {
    }
}
