package com.chasehorse.erp.approval;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.chasehorse.erp.ai.AiVerificationService;
import com.chasehorse.erp.approval.ReviewDtos.*;
import com.chasehorse.erp.common.ApiException;
import com.chasehorse.erp.common.Paging;
import com.chasehorse.erp.common.Rs;
import com.chasehorse.erp.db.ActorDb;
import com.chasehorse.erp.evidence.EvidenceDtos.Evidence;
import com.chasehorse.erp.evidence.EvidenceService;
import com.chasehorse.erp.checklist.ChecklistService;
import com.chasehorse.erp.security.Actor;
import com.chasehorse.erp.security.AppRole;
import com.chasehorse.erp.task.TaskService;

@Service
public class ReviewService {
    private final ActorDb db;
    private final TaskService tasks;
    private final EvidenceService evidence;
    private final ChecklistService checklists;
    private final AiVerificationService ai;

    public ReviewService(ActorDb db, TaskService tasks, EvidenceService evidence, ChecklistService checklists, AiVerificationService ai) {
        this.db = db;
        this.tasks = tasks;
        this.evidence = evidence;
        this.checklists = checklists;
        this.ai = ai;
    }

    private static void requireStageRole(Actor actor, String stage) {
        if ("manager".equals(stage)) {
            actor.requireAny(AppRole.ADMIN, AppRole.MANAGER);
        } else if ("operations".equals(stage)) {
            actor.requireAny(AppRole.ADMIN, AppRole.OPERATIONS);
        } else {
            throw ApiException.invalid("stage must be 'manager' or 'operations'.");
        }
    }

    private static final String COLS = """
            SELECT t.id AS task_id, t.task_no, t.title, co.name AS customer_name, t.assigned_to, ep.display_name AS employee_name,
                   t.submission_no, t.submitted_at, t.reward_paise, t.status::text AS task_status,
                   ai.status::text AS ai_status, ai.recommendation::text AS ai_rec
            """;
    private static final String JOINS = """
              JOIN app.organizations co ON co.id = t.customer_org_id
              LEFT JOIN app.profiles ep ON ep.id = t.assigned_to
              LEFT JOIN LATERAL (SELECT a.status, a.recommendation FROM app.ai_verification_results a
                                  WHERE a.task_id = t.id AND a.submission_no = t.submission_no
                                  ORDER BY a.requested_at DESC LIMIT 1) ai ON true
            """;

    /** Review queue. state=pending: waiting for this stage. state=decided: decisions already made at this stage. */
    public List<ReviewSummary> queue(Actor actor, String stage, String state, Paging paging) {
        requireStageRole(actor, stage);
        boolean pending = !"decided".equals(state);
        return db.run(actor.id(), j -> {
            if (pending) {
                return j.sql(COLS + ", NULL::text AS decision, NULL::uuid AS decided_by, NULL::text AS decided_by_name, NULL::timestamptz AS decided_at, NULL::text AS reason "
                        + " FROM app.tasks t " + JOINS + " WHERE t.status = CAST(:st AS app.task_status) ORDER BY t.submitted_at LIMIT :limit OFFSET :offset")
                        .param("st", "manager".equals(stage) ? "submitted" : "manager_approved")
                        .param("limit", paging.limit()).param("offset", paging.offset())
                        .query((rs, n) -> row(rs)).list();
            }
            return j.sql(COLS + ", d.decision::text AS decision, d.decided_by, dp.display_name AS decided_by_name, d.decided_at, d.reason "
                    + " FROM app.approval_decisions d JOIN app.tasks t ON t.id = d.task_id " + JOINS
                    + " LEFT JOIN app.profiles dp ON dp.id = d.decided_by "
                    + " WHERE d.stage = CAST(:stage AS app.review_stage) ORDER BY d.decided_at DESC LIMIT :limit OFFSET :offset")
                    .param("stage", stage).param("limit", paging.limit()).param("offset", paging.offset())
                    .query((rs, n) -> row(rs)).list();
        });
    }

    private static ReviewSummary row(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new ReviewSummary(Rs.uuid(rs, "task_id"), rs.getLong("task_no"), rs.getString("title"), rs.getString("customer_name"),
                Rs.uuid(rs, "assigned_to"), rs.getString("employee_name"), rs.getInt("submission_no"), Rs.ts(rs, "submitted_at"),
                rs.getLong("reward_paise"), rs.getString("task_status"), rs.getString("ai_status"), rs.getString("ai_rec"),
                rs.getString("decision"), Rs.uuid(rs, "decided_by"), rs.getString("decided_by_name"), Rs.ts(rs, "decided_at"),
                rs.getString("reason"));
    }

    public ReviewPackage reviewPackage(Actor actor, UUID taskId) {
        actor.requireStaff();
        var task = tasks.get(actor, taskId);
        List<Evidence> ev = evidence.forTask(actor, taskId).stream().filter(e -> e.submissionNo() == Math.max(task.submissionNo(), 1)).toList();
        var checklist = checklists.forTask(actor, taskId);
        return db.run(actor.id(), j -> new ReviewPackage(task, ev, checklist, AiVerificationService.latest(j, taskId),
                j.sql("""
                        SELECT d.id, d.submission_no, d.stage::text AS stage, d.decision::text AS decision, d.decided_by,
                               p.display_name AS decided_by_name, d.reason, d.ai_result_id, d.decided_at
                          FROM app.approval_decisions d LEFT JOIN app.profiles p ON p.id = d.decided_by
                         WHERE d.task_id = :t ORDER BY d.decided_at
                        """).param("t", taskId)
                        .query((rs, n) -> new DecisionRow(Rs.uuid(rs, "id"), rs.getInt("submission_no"), rs.getString("stage"),
                                rs.getString("decision"), Rs.uuid(rs, "decided_by"), rs.getString("decided_by_name"),
                                rs.getString("reason"), Rs.uuid(rs, "ai_result_id"), Rs.ts(rs, "decided_at")))
                        .list()));
    }

    /**
     * A human decision. The database enforces the order (manager first, then operations), that nobody reviews
     * their own work and that the operations approver differs from the manager reviewer. On the final
     * (operations) approval the reward is credited to the employee's internal ledger in the SAME transaction,
     * so an approval can never exist without its (idempotent) credit.
     */
    public DecisionResult decide(Actor actor, UUID taskId, DecisionRequest r) {
        requireStageRole(actor, r.stage());
        if (!"approved".equals(r.decision()) && (r.reason() == null || r.reason().trim().length() < 3)) {
            throw ApiException.invalid("A reason is required when rejecting or requesting rework.");
        }
        return db.run(actor.id(), j -> {
            String status = j.sql("SELECT app.decide_task(:t, CAST(:s AS app.review_stage), CAST(:d AS app.review_decision), :r)::text")
                    .param("t", taskId).param("s", r.stage()).param("d", r.decision()).param("r", r.reason())
                    .query(String.class).single();
            UUID entry = null;
            if ("approved".equals(status)) {
                long reward = j.sql("SELECT reward_paise FROM app.tasks WHERE id = :t").param("t", taskId).query(Long.class).single();
                if (reward > 0) {
                    entry = j.sql("SELECT app.credit_task_reward(:t)").param("t", taskId).query(UUID.class).single();
                }
            }
            return new DecisionResult(taskId, status, entry != null, entry);
        });
    }

    /** Staff can (re-)request the advisory AI check. It never changes approval state. */
    public AiRequestResult requestAi(Actor actor, UUID taskId) {
        actor.requireStaff();
        tasks.get(actor, taskId);
        return new AiRequestResult(ai.requestVerification(actor.id(), taskId));
    }
}
