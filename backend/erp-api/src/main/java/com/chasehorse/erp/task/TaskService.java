package com.chasehorse.erp.task;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import com.chasehorse.erp.ai.AiVerificationService;
import com.chasehorse.erp.common.ApiException;
import com.chasehorse.erp.common.Money;
import com.chasehorse.erp.common.Paging;
import com.chasehorse.erp.common.Pg;
import com.chasehorse.erp.common.Rs;
import com.chasehorse.erp.db.ActorDb;
import com.chasehorse.erp.security.Actor;
import com.chasehorse.erp.security.AppRole;
import com.chasehorse.erp.task.TaskDtos.*;

@Service
public class TaskService {
    private static final Logger log = LoggerFactory.getLogger(TaskService.class);

    private static final String SELECT = """
            SELECT t.id, t.task_no, t.title, t.description, t.category, t.priority::text AS priority, t.status::text AS status,
                   t.customer_org_id, co.name AS customer_name, t.partner_org_id, t.address, t.city, t.state, t.postal_code,
                   t.lat, t.lng, t.completion_radius_m, t.reward_paise, t.available_from, t.due_at, t.required_skills,
                   t.checklist_version_id, t.assigned_to, ap.display_name AS assignee_name, t.accepted_at,
                   t.submission_no, t.submitted_at, t.version, t.created_at, t.updated_at
            """;
    private static final String FROM = """
              FROM app.tasks t
              JOIN app.organizations co ON co.id = t.customer_org_id
              LEFT JOIN app.profiles ap ON ap.id = t.assigned_to
            """;

    private final ActorDb db;
    private final AiVerificationService ai;

    public TaskService(ActorDb db, AiVerificationService ai) {
        this.db = db;
        this.ai = ai;
    }

    // ---- visibility -----------------------------------------------------------

    /** SQL predicate limiting tasks to what this caller may see. Parameter :me must be bound. */
    public static String visibility(Actor actor) {
        if (actor.isStaff()) {
            return "TRUE";
        }
        List<String> parts = new ArrayList<>();
        if (actor.has(AppRole.CUSTOMER)) {
            parts.add("t.customer_org_id IN (SELECT org_id FROM app.org_members WHERE user_id = :me)");
        }
        if (actor.has(AppRole.PARTNER)) {
            parts.add("t.partner_org_id IN (SELECT org_id FROM app.org_members WHERE user_id = :me)");
        }
        if (actor.has(AppRole.EMPLOYEE)) {
            parts.add("t.assigned_to = :me");
        }
        return parts.isEmpty() ? "FALSE" : "(" + String.join(" OR ", parts) + ")";
    }

    static TaskDto map(ResultSet rs, Actor actor) throws SQLException {
        UUID assignee = Rs.uuid(rs, "assigned_to");
        boolean seesPeople = actor.isStaff() || actor.id().equals(assignee);
        boolean seesReward = actor.isStaff() || actor.id().equals(assignee) || actor.has(AppRole.EMPLOYEE);
        return new TaskDto(Rs.uuid(rs, "id"), rs.getLong("task_no"), rs.getString("title"), rs.getString("description"),
                rs.getString("category"), rs.getString("priority"), rs.getString("status"), Rs.uuid(rs, "customer_org_id"),
                rs.getString("customer_name"), Rs.uuid(rs, "partner_org_id"), rs.getString("address"), rs.getString("city"),
                rs.getString("state"), rs.getString("postal_code"), rs.getBigDecimal("lat").doubleValue(),
                rs.getBigDecimal("lng").doubleValue(), rs.getInt("completion_radius_m"),
                seesReward ? rs.getLong("reward_paise") : null, Rs.ts(rs, "available_from"), Rs.ts(rs, "due_at"),
                Pg.toList(rs.getArray("required_skills")), Rs.uuid(rs, "checklist_version_id"),
                seesPeople ? assignee : null, seesPeople ? rs.getString("assignee_name") : null, Rs.ts(rs, "accepted_at"),
                rs.getInt("submission_no"), Rs.ts(rs, "submitted_at"), rs.getLong("version"), Rs.ts(rs, "created_at"),
                Rs.ts(rs, "updated_at"));
    }

    // ---- reads ----------------------------------------------------------------

    public List<TaskDto> list(Actor actor, String status, UUID customerOrgId, Paging paging) {
        actor.requireActive();
        return db.run(actor.id(), j -> j.sql(SELECT + FROM + " WHERE " + visibility(actor) + """
                   AND (CAST(:status AS text) IS NULL OR t.status::text = :status)
                   AND (CAST(:org AS uuid) IS NULL OR t.customer_org_id = :org)
                 ORDER BY t.created_at DESC LIMIT :limit OFFSET :offset
                """).param("me", actor.id()).param("status", status).param("org", customerOrgId)
                .param("limit", paging.limit()).param("offset", paging.offset())
                .query((rs, n) -> map(rs, actor)).list());
    }

    public List<TaskDto> mine(Actor actor, Paging paging) {
        actor.requireAny(AppRole.EMPLOYEE);
        return db.run(actor.id(), j -> j.sql(SELECT + FROM + """
                 WHERE t.assigned_to = :me
                 ORDER BY t.updated_at DESC LIMIT :limit OFFSET :offset
                """).param("me", actor.id()).param("limit", paging.limit()).param("offset", paging.offset())
                .query((rs, n) -> map(rs, actor)).list());
    }

    public TaskDto get(Actor actor, UUID id) {
        actor.requireActive();
        return db.run(actor.id(), j -> load(j, actor, id));
    }

    public static TaskDto load(JdbcClient j, Actor actor, UUID id) {
        return j.sql(SELECT + FROM + " WHERE t.id = :id AND " + visibility(actor))
                .param("id", id).param("me", actor.id())
                .query((rs, n) -> map(rs, actor)).optional()
                // 404 (not 403) so the existence of tasks a caller may not see is not revealed
                .orElseThrow(() -> ApiException.notFound("Task not found."));
    }

    public List<NearbyTask> nearby(Actor actor, Paging paging) {
        actor.requireAny(AppRole.EMPLOYEE);
        return db.run(actor.id(), j -> j.sql(SELECT + ", n.distance_km " + """
                  FROM app.nearby_open_tasks(:limit, :offset) n
                  JOIN app.tasks t ON t.id = n.task_id
                  JOIN app.organizations co ON co.id = t.customer_org_id
                  LEFT JOIN app.profiles ap ON ap.id = t.assigned_to
                 ORDER BY n.distance_km, t.created_at
                """).param("limit", paging.limit()).param("offset", paging.offset())
                .query((rs, n) -> new NearbyTask(map(rs, actor), rs.getDouble("distance_km"))).list());
    }

    public List<HistoryEntry> history(Actor actor, UUID id) {
        actor.requireActive();
        return db.run(actor.id(), j -> {
            load(j, actor, id); // visibility check
            return j.sql("""
                    SELECT h.id, h.from_status::text AS from_status, h.to_status::text AS to_status, h.actor_id,
                           p.display_name AS actor_name, h.occurred_at
                      FROM app.task_status_history h LEFT JOIN app.profiles p ON p.id = h.actor_id
                     WHERE h.task_id = :id ORDER BY h.occurred_at, h.id
                    """).param("id", id)
                    .query((rs, n) -> new HistoryEntry(Rs.uuid(rs, "id"), rs.getString("from_status"), rs.getString("to_status"),
                            Rs.uuid(rs, "actor_id"), actor.isStaff() ? rs.getString("actor_name") : null, Rs.ts(rs, "occurred_at")))
                    .list();
        });
    }

    // ---- writes ---------------------------------------------------------------

    public TaskDto create(Actor actor, CreateTaskRequest r) {
        actor.requireAny(AppRole.ADMIN, AppRole.MANAGER, AppRole.OPERATIONS, AppRole.CUSTOMER);
        long reward = r.rewardPaise() == null ? 0 : r.rewardPaise();
        Money.requireValidReward(reward);
        if (!actor.isStaff() && reward != 0) {
            throw ApiException.forbidden("Rewards are set by ChaseHorse staff.");
        }
        return db.run(actor.id(), j -> {
            UUID id = j.sql("""
                    SELECT app.create_task(:title, :description, :category, CAST(:priority AS app.task_priority), :customer, :partner,
                                           :address, :city, :state, :postal, :lat, :lng, :radius, :reward, :availableFrom, :dueAt,
                                           CAST(:skills AS text[]), :checklist)
                    """)
                    .param("title", r.title().trim()).param("description", r.description())
                    .param("category", r.category()).param("priority", r.priority() == null ? "normal" : r.priority())
                    .param("customer", r.customerOrgId()).param("partner", r.partnerOrgId())
                    .param("address", r.address()).param("city", r.city()).param("state", r.state()).param("postal", r.postalCode())
                    .param("lat", r.lat()).param("lng", r.lng()).param("radius", r.completionRadiusM())
                    .param("reward", reward).param("availableFrom", r.availableFrom()).param("dueAt", r.dueAt())
                    .param("skills", Pg.textArray(r.requiredSkills())).param("checklist", r.checklistVersionId())
                    .query(UUID.class).single();
            return load(j, actor, id);
        });
    }

    public TaskDto setReward(Actor actor, UUID id, long rewardPaise) {
        actor.requireStaff();
        Money.requireValidReward(rewardPaise);
        return db.run(actor.id(), j -> {
            ActorDb.call(j, "SELECT app.set_task_reward(:t, :r)", Map.of("t", id, "r", rewardPaise));
            return load(j, actor, id);
        });
    }

    public TaskDto assign(Actor actor, UUID id, UUID employeeId) {
        actor.requireStaff();
        return db.run(actor.id(), j -> {
            ActorDb.call(j, "SELECT app.assign_task(:t, :e)", Map.of("t", id, "e", employeeId));
            return load(j, actor, id);
        });
    }

    public TaskDto cancel(Actor actor, UUID id, String reason) {
        actor.requireStaff();
        return db.run(actor.id(), j -> {
            ActorDb.call(j, "SELECT app.cancel_task(:t, :r)", Map.of("t", id, "r", reason));
            return load(j, actor, id);
        });
    }

    /** Atomic first-accept-wins: the database allows exactly one employee; everyone else gets a 409. */
    public TaskDto accept(Actor actor, UUID id) {
        actor.requireAny(AppRole.EMPLOYEE);
        return db.run(actor.id(), j -> {
            ActorDb.call(j, "SELECT app.accept_task(:t)", Map.of("t", id));
            return load(j, actor, id);
        });
    }

    public void rejectOffer(Actor actor, UUID id, String reason) {
        actor.requireAny(AppRole.EMPLOYEE);
        db.runVoid(actor.id(), j -> j.sql("SELECT app.reject_task_offer(:t, :r)").param("t", id).param("r", reason)
                .query((rs, n) -> 1).list());
    }

    public TaskDto start(Actor actor, UUID id) {
        actor.requireAny(AppRole.EMPLOYEE);
        return db.run(actor.id(), j -> {
            ActorDb.call(j, "SELECT app.start_task(:t)", Map.of("t", id));
            return load(j, actor, id);
        });
    }

    /**
     * Submit finished work. The database refuses unless every required checklist item is done and every
     * required photo is uploaded. After the commit an AI check is REQUESTED (advisory only; with no
     * provider configured it records "manual review required"). A failure there never undoes the submission.
     */
    public SubmissionResult submit(Actor actor, UUID id) {
        actor.requireAny(AppRole.EMPLOYEE);
        int submissionNo = db.run(actor.id(), j -> j.sql("SELECT app.submit_task(:t)").param("t", id).query(Integer.class).single());
        String aiStatus;
        try {
            aiStatus = ai.requestVerification(actor.id(), id);
        } catch (RuntimeException e) {
            log.warn("AI verification request failed for task {} (submission is unaffected)", id, e);
            aiStatus = "not_requested";
        }
        return new SubmissionResult(id, submissionNo, aiStatus);
    }
}
