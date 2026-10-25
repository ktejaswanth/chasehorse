package com.chasehorse.erp.wallet;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import com.chasehorse.erp.common.ApiException;
import com.chasehorse.erp.common.Paging;
import com.chasehorse.erp.common.Rs;
import com.chasehorse.erp.db.ActorDb;
import com.chasehorse.erp.security.Actor;
import com.chasehorse.erp.security.AppRole;
import com.chasehorse.erp.wallet.WalletDtos.*;

@Service
public class WalletService {
    private final ActorDb db;

    public WalletService(ActorDb db) {
        this.db = db;
    }

    private static final String SUMMARY = """
            SELECT p.id AS user_id, p.display_name, coalesce(w.balance_paise, 0) AS balance,
                   coalesce((SELECT sum(t.reward_paise) FROM app.tasks t
                              WHERE t.assigned_to = p.id AND t.status IN ('submitted', 'manager_approved')), 0)::bigint AS pending
              FROM app.profiles p LEFT JOIN app.wallet_accounts w ON w.user_id = p.id
            """;

    private static WalletSummary summary(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new WalletSummary(Rs.uuid(rs, "user_id"), rs.getString("display_name"), "INR", rs.getLong("balance"), rs.getLong("pending"), false);
    }

    public MyWallet mine(Actor actor) {
        actor.requireAny(AppRole.EMPLOYEE);
        return db.run(actor.id(), j -> new MyWallet(
                j.sql(SUMMARY + " WHERE p.id = :me").param("me", actor.id()).query((rs, n) -> summary(rs)).single(),
                entries(j, actor.id(), new Paging(50, 0))));
    }

    /** Staff overview of employee wallets. */
    public List<WalletSummary> list(Actor actor, Paging paging) {
        actor.requireStaff();
        return db.run(actor.id(), j -> j.sql(SUMMARY + """
                 JOIN app.user_roles r ON r.user_id = p.id AND r.role = 'employee'
                 WHERE p.account_status = 'active'
                 ORDER BY p.display_name NULLS LAST, p.id LIMIT :limit OFFSET :offset
                """).param("limit", paging.limit()).param("offset", paging.offset())
                .query((rs, n) -> summary(rs)).list());
    }

    public List<LedgerEntry> transactions(Actor actor, UUID userId, Paging paging) {
        actor.requireActive();
        if (!actor.isStaff() && !actor.id().equals(userId)) {
            throw ApiException.forbidden("You may only view your own transactions.");
        }
        return db.run(actor.id(), j -> entries(j, userId, paging));
    }

    private static List<LedgerEntry> entries(JdbcClient j, UUID userId, Paging paging) {
        return j.sql("""
                SELECT id, seq, entry_type::text AS entry_type, amount_paise, balance_after_paise, task_id, reverses_entry_id, memo, created_at
                  FROM app.wallet_ledger_entries WHERE user_id = :u ORDER BY seq DESC LIMIT :limit OFFSET :offset
                """).param("u", userId).param("limit", paging.limit()).param("offset", paging.offset())
                .query((rs, n) -> new LedgerEntry(Rs.uuid(rs, "id"), rs.getLong("seq"), rs.getString("entry_type"), rs.getLong("amount_paise"),
                        rs.getLong("balance_after_paise"), Rs.uuid(rs, "task_id"), Rs.uuid(rs, "reverses_entry_id"),
                        rs.getString("memo"), Rs.ts(rs, "created_at")))
                .list();
    }

    /** Idempotent: calling twice returns the same ledger entry and never credits twice. */
    public SettlementResult settle(Actor actor, UUID taskId) {
        actor.requireAny(AppRole.ADMIN, AppRole.OPERATIONS);
        UUID entry = db.run(actor.id(), j -> j.sql("SELECT app.credit_task_reward(:t)").param("t", taskId).query(UUID.class).single());
        return new SettlementResult(taskId, entry);
    }

    public ReversalResult reverse(Actor actor, UUID entryId, String reason) {
        actor.requireAny(AppRole.ADMIN);
        UUID rev = db.run(actor.id(), j -> j.sql("SELECT app.reverse_ledger_entry(:e, :r)").param("e", entryId).param("r", reason)
                .query(UUID.class).single());
        return new ReversalResult(entryId, rev);
    }

    public List<Discrepancy> reconcile(Actor actor) {
        actor.requireAny(AppRole.ADMIN, AppRole.OPERATIONS);
        return db.run(actor.id(), j -> j.sql("SELECT user_id, cached_paise, ledger_paise FROM app.wallet_discrepancies()")
                .query((rs, n) -> new Discrepancy(Rs.uuid(rs, "user_id"), rs.getLong("cached_paise"), rs.getLong("ledger_paise"))).list());
    }
}
