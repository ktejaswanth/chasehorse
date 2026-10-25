package com.chasehorse.erp.db;

import java.util.UUID;
import java.util.function.Function;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Runs database work as a specific, ALREADY-VERIFIED user.
 *
 * The API connects with one pooled database role. The database rules (see
 * database/migrations) need to know who is acting, so every unit of work runs in
 * a transaction that first sets the transaction-local setting app.actor_id to the
 * user id taken from the verified JWT. Because it is transaction-local
 * (set_config(..., true)) it can never leak to another request on the same
 * pooled connection. The id must come from the verified token, never from
 * request data.
 */
@Component
public class ActorDb {
    private final TransactionTemplate tx;
    private final JdbcClient jdbc;

    public ActorDb(PlatformTransactionManager txManager, JdbcClient jdbc) {
        this.tx = new TransactionTemplate(txManager);
        this.jdbc = jdbc;
    }

    public <T> T run(UUID actorId, Function<JdbcClient, T> work) {
        return tx.execute(status -> {
            jdbc.sql("SELECT set_config('app.actor_id', :actor, true)")
                    .param("actor", actorId.toString())
                    .query((rs, n) -> 1)
                    .list();
            return work.apply(jdbc);
        });
    }

    public void runVoid(UUID actorId, java.util.function.Consumer<JdbcClient> work) {
        run(actorId, j -> {
            work.accept(j);
            return null;
        });
    }

    /** Calls a database function that returns void. */
    public static void call(JdbcClient j, String sql, java.util.Map<String, ?> params) {
        j.sql(sql).params(params).query((rs, n) -> 1).list();
    }
}
