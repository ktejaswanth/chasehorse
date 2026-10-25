package com.chasehorse.erp.admin;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.chasehorse.erp.common.Paging;
import com.chasehorse.erp.common.Rs;
import com.chasehorse.erp.config.AppProperties;
import com.chasehorse.erp.db.ActorDb;
import com.chasehorse.erp.security.Actor;
import com.chasehorse.erp.security.AppRole;
import com.chasehorse.erp.security.CurrentActor;

/** Read-only view of the append-only audit trail (administrators only). */
@RestController
@RequestMapping("/api/v1/audit-events")
public class AuditController {
    public record AuditEvent(UUID id, UUID actorId, String action, String entityType, UUID entityId, String metadata, OffsetDateTime occurredAt) {
    }

    private final ActorDb db;
    private final int maxPage;

    public AuditController(ActorDb db, AppProperties props) {
        this.db = db;
        this.maxPage = props.limits() == null || props.limits().maxPageSize() <= 0 ? 200 : props.limits().maxPageSize();
    }

    @GetMapping
    public List<AuditEvent> list(@CurrentActor Actor actor, @RequestParam(required = false) String entityType,
                                 @RequestParam(required = false) UUID entityId, @RequestParam(defaultValue = "0") int page,
                                 @RequestParam(defaultValue = "50") int size) {
        actor.requireAny(AppRole.ADMIN);
        Paging p = Paging.of(page, size, maxPage);
        return db.run(actor.id(), j -> j.sql("""
                SELECT id, actor_id, action, entity_type, entity_id, metadata::text AS metadata, occurred_at
                  FROM app.audit_events
                 WHERE (CAST(:type AS text) IS NULL OR entity_type = :type) AND (CAST(:eid AS uuid) IS NULL OR entity_id = :eid)
                 ORDER BY occurred_at DESC LIMIT :limit OFFSET :offset
                """).param("type", entityType).param("eid", entityId).param("limit", p.limit()).param("offset", p.offset())
                .query((rs, n) -> new AuditEvent(Rs.uuid(rs, "id"), Rs.uuid(rs, "actor_id"), rs.getString("action"),
                        rs.getString("entity_type"), Rs.uuid(rs, "entity_id"), rs.getString("metadata"), Rs.ts(rs, "occurred_at")))
                .list());
    }
}
