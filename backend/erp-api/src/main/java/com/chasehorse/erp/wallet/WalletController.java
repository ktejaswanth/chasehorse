package com.chasehorse.erp.wallet;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.*;

import com.chasehorse.erp.common.Paging;
import com.chasehorse.erp.config.AppProperties;
import com.chasehorse.erp.security.Actor;
import com.chasehorse.erp.security.CurrentActor;
import com.chasehorse.erp.wallet.WalletDtos.*;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1")
public class WalletController {
    private final WalletService wallets;
    private final int maxPage;

    public WalletController(WalletService wallets, AppProperties props) {
        this.wallets = wallets;
        this.maxPage = props.limits() == null || props.limits().maxPageSize() <= 0 ? 200 : props.limits().maxPageSize();
    }

    @GetMapping("/wallets/me")
    public MyWallet mine(@CurrentActor Actor actor) {
        return wallets.mine(actor);
    }

    @GetMapping("/wallets")
    public List<WalletSummary> list(@CurrentActor Actor actor, @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "50") int size) {
        return wallets.list(actor, Paging.of(page, size, maxPage));
    }

    @GetMapping("/wallets/reconciliation")
    public List<Discrepancy> reconcile(@CurrentActor Actor actor) {
        return wallets.reconcile(actor);
    }

    @GetMapping("/wallets/{userId}/transactions")
    public List<LedgerEntry> transactions(@CurrentActor Actor actor, @PathVariable UUID userId,
                                          @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return wallets.transactions(actor, userId, Paging.of(page, size, maxPage));
    }

    @PostMapping("/tasks/{taskId}/reward-settlement")
    public SettlementResult settle(@CurrentActor Actor actor, @PathVariable UUID taskId) {
        return wallets.settle(actor, taskId);
    }

    @PostMapping("/ledger-entries/{entryId}/reversal")
    public ReversalResult reverse(@CurrentActor Actor actor, @PathVariable UUID entryId, @Valid @RequestBody ReversalRequest body) {
        return wallets.reverse(actor, entryId, body.reason());
    }
}
