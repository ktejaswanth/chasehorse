package com.chasehorse.erp.user;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.chasehorse.erp.common.ApiException;
import com.chasehorse.erp.common.Paging;
import com.chasehorse.erp.common.Pg;
import com.chasehorse.erp.db.ActorDb;
import com.chasehorse.erp.security.Actor;
import com.chasehorse.erp.security.AppRole;
import com.chasehorse.erp.user.UserDtos.*;

@Service
public class UserService {
    private final ActorDb db;

    public UserService(ActorDb db) {
        this.db = db;
    }

    public Me me(Actor actor) {
        return db.run(actor.id(), j -> j.sql("""
                SELECT p.id, p.email, p.display_name, p.phone, p.account_status::text AS status,
                       (p.location_consent_at IS NOT NULL) AS consent,
                       coalesce((SELECT array_agg(r.role::text ORDER BY r.role) FROM app.user_roles r WHERE r.user_id = p.id), '{}') AS roles
                  FROM app.profiles p WHERE p.id = :id
                """).param("id", actor.id())
                .query((rs, n) -> new Me(rs.getObject("id", UUID.class), rs.getString("email"), rs.getString("display_name"),
                        rs.getString("phone"), rs.getString("status"), Pg.toList(rs.getArray("roles")), true, rs.getBoolean("consent")))
                .optional()
                .orElseGet(() -> new Me(actor.id(), actor.email(), null, null, "unregistered", List.of(), false, false)));
    }

    public Me register(Actor actor, RegisterRequest r) {
        if (actor.email() == null || actor.email().isBlank()) {
            throw ApiException.invalid("Your sign-in has no verified email address.");
        }
        db.runVoid(actor.id(), j -> j.sql("SELECT app.register_employee(:email, :name, :phone, :region)")
                .param("email", actor.email()).param("name", r.displayName())
                .param("phone", r.phone()).param("region", r.region())
                .query((rs, n) -> 1).list());
        return me(actor);
    }

    public void setLocationConsent(Actor actor, boolean consent) {
        db.runVoid(actor.id(), j -> ActorDb.call(j, "SELECT app.set_location_consent(:c)", Map.of("c", consent)));
    }

    public void updateLocation(Actor actor, LocationRequest r) {
        db.runVoid(actor.id(), j -> j.sql("SELECT app.update_my_location(:lat, :lng, :acc, :at)")
                .param("lat", r.lat()).param("lng", r.lng()).param("acc", r.accuracyM())
                .param("at", r.capturedAt() == null ? java.time.OffsetDateTime.now() : r.capturedAt())
                .query((rs, n) -> 1).list());
    }

    /** Staff view of employees. Last location is only returned for people who gave location consent. */
    public List<EmployeeSummary> listEmployees(Actor actor, String status, Paging paging) {
        actor.requireStaff();
        return db.run(actor.id(), j -> j.sql("""
                SELECT p.id, p.email, p.display_name, p.phone, p.account_status::text AS status, p.created_at,
                       e.region, e.skills, e.search_radius_km,
                       CASE WHEN p.location_consent_at IS NOT NULL THEN l.lat END AS lat,
                       CASE WHEN p.location_consent_at IS NOT NULL THEN l.lng END AS lng,
                       CASE WHEN p.location_consent_at IS NOT NULL THEN l.captured_at END AS loc_at,
                       (SELECT count(*) FROM app.tasks t WHERE t.assigned_to = p.id AND t.status = 'approved') AS completed,
                       EXISTS (SELECT 1 FROM app.tasks t WHERE t.assigned_to = p.id
                                AND t.status IN ('accepted','in_progress','submitted','rework_requested','manager_approved')) AS on_task
                  FROM app.profiles p
                  JOIN app.employees e ON e.user_id = p.id
                  LEFT JOIN app.employee_locations l ON l.user_id = p.id
                 WHERE (CAST(:status AS text) IS NULL OR p.account_status::text = :status)
                 ORDER BY p.created_at DESC
                 LIMIT :limit OFFSET :offset
                """).param("status", status).param("limit", paging.limit()).param("offset", paging.offset())
                .query((rs, n) -> new EmployeeSummary(rs.getObject("id", UUID.class), rs.getString("email"), rs.getString("display_name"),
                        rs.getString("phone"), rs.getString("region"), Pg.toList(rs.getArray("skills")), rs.getString("status"),
                        rs.getBoolean("on_task"), rs.getLong("completed"), rs.getInt("search_radius_km"),
                        com.chasehorse.erp.common.Rs.dbl(rs, "lat"), com.chasehorse.erp.common.Rs.dbl(rs, "lng"),
                        rs.getObject("loc_at", java.time.OffsetDateTime.class), rs.getObject("created_at", java.time.OffsetDateTime.class)))
                .list());
    }

    public AccountDecisionResponse decideAccount(Actor actor, UUID userId, AccountDecisionRequest r) {
        actor.requireAny(AppRole.ADMIN, AppRole.MANAGER);
        String status = db.run(actor.id(), j -> j.sql("SELECT app.decide_account(:u, :d, :r)::text")
                .param("u", userId).param("d", r.decision()).param("r", r.reason())
                .query(String.class).single());
        return new AccountDecisionResponse(userId, status);
    }

    // ---- administration -------------------------------------------------------

    public List<AccountRow> listAccounts(Actor actor, Paging paging) {
        actor.requireAny(AppRole.ADMIN);
        return db.run(actor.id(), j -> j.sql("""
                SELECT p.id, p.email, p.display_name, p.account_status::text AS status, p.created_at,
                       coalesce((SELECT array_agg(r.role::text ORDER BY r.role) FROM app.user_roles r WHERE r.user_id = p.id), '{}') AS roles
                  FROM app.profiles p ORDER BY p.created_at DESC LIMIT :limit OFFSET :offset
                """).param("limit", paging.limit()).param("offset", paging.offset())
                .query((rs, n) -> new AccountRow(rs.getObject("id", UUID.class), rs.getString("email"), rs.getString("display_name"),
                        rs.getString("status"), Pg.toList(rs.getArray("roles")), rs.getObject("created_at", java.time.OffsetDateTime.class)))
                .list());
    }

    /**
     * Admin-only: create the profile + role (+ organisation membership) for a person who already has a
     * Supabase Auth user, and activate it. Privilege rules are enforced again by database triggers.
     */
    public AccountDecisionResponse provision(Actor actor, ProvisionRequest r) {
        actor.requireAny(AppRole.ADMIN);
        if (("customer".equals(r.role()) || "partner".equals(r.role())) && r.organizationId() == null) {
            throw ApiException.invalid("organizationId is required for customer and partner accounts.");
        }
        String status = db.run(actor.id(), j -> {
            if (r.organizationId() != null) {
                String kind = j.sql("SELECT kind::text FROM app.organizations WHERE id = :o AND active")
                        .param("o", r.organizationId()).query(String.class).optional()
                        .orElseThrow(() -> ApiException.invalid("Organisation not found or inactive."));
                if (("customer".equals(r.role()) || "partner".equals(r.role())) && !kind.equals(r.role())) {
                    throw ApiException.invalid("A " + r.role() + " account must belong to a " + r.role() + " organisation.");
                }
            }
            j.sql("INSERT INTO app.profiles (id, email, display_name) VALUES (:id, :email, :name)")
                    .param("id", r.userId()).param("email", r.email()).param("name", r.displayName()).update();
            j.sql("INSERT INTO app.user_roles (user_id, role) VALUES (:id, CAST(:role AS app.app_role))")
                    .param("id", r.userId()).param("role", r.role()).update();
            if ("employee".equals(r.role())) {
                j.sql("INSERT INTO app.employees (user_id) VALUES (:id)").param("id", r.userId()).update();
            }
            if (r.organizationId() != null) {
                j.sql("INSERT INTO app.org_members (org_id, user_id) VALUES (:o, :u)")
                        .param("o", r.organizationId()).param("u", r.userId()).update();
            }
            return j.sql("SELECT app.decide_account(:u, 'approved', 'provisioned by administrator')::text")
                    .param("u", r.userId()).query(String.class).single();
        });
        return new AccountDecisionResponse(r.userId(), status);
    }

    public void revokeRole(Actor actor, UUID userId, String role) {
        actor.requireAny(AppRole.ADMIN);
        if (actor.id().equals(userId)) {
            throw ApiException.forbidden("You cannot revoke your own role.");
        }
        int rows = db.run(actor.id(), j -> j.sql("DELETE FROM app.user_roles WHERE user_id = :u AND role = CAST(:r AS app.app_role)")
                .param("u", userId).param("r", role).update());
        if (rows == 0) {
            throw ApiException.notFound("That role assignment does not exist.");
        }
    }

    public List<Organization> listOrganizations(Actor actor, String kind) {
        actor.requireActive();
        boolean staff = actor.isStaff();
        return db.run(actor.id(), j -> j.sql("""
                SELECT o.id, o.name, o.kind::text AS kind, o.active FROM app.organizations o
                 WHERE (CAST(:kind AS text) IS NULL OR o.kind::text = :kind)
                   AND (:staff OR o.id IN (SELECT org_id FROM app.org_members WHERE user_id = :me))
                 ORDER BY o.name
                """).param("kind", kind).param("staff", staff).param("me", actor.id())
                .query((rs, n) -> new Organization(rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("kind"), rs.getBoolean("active")))
                .list());
    }

    public Organization createOrganization(Actor actor, NewOrganization r) {
        actor.requireAny(AppRole.ADMIN, AppRole.MANAGER);
        return db.run(actor.id(), j -> {
            Organization o = j.sql("INSERT INTO app.organizations (name, kind) VALUES (:n, CAST(:k AS app.org_kind)) RETURNING id, name, kind::text AS kind, active")
                    .param("n", r.name()).param("k", r.kind())
                    .query((rs, n) -> new Organization(rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("kind"), rs.getBoolean("active")))
                    .single();
            ActorDb.call(j, "SELECT app.record_audit('organization.created', 'organization', :id, '{}'::jsonb)", Map.of("id", o.id()));
            return o;
        });
    }
}
