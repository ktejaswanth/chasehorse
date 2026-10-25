package com.chasehorse.erp.ai;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import com.chasehorse.erp.common.Pg;
import com.chasehorse.erp.common.Rs;
import com.chasehorse.erp.db.ActorDb;

@Service
public class AiVerificationService {
    private final ActorDb db;
    private final AiVerificationProvider provider;

    public AiVerificationService(ActorDb db, AiVerificationProvider provider) {
        this.db = db;
        this.provider = provider;
    }

    public record AiResult(UUID id, int submissionNo, String status, String recommendation, BigDecimal confidence,
                           List<String> reasonCodes, String summary, String provider, String modelVersion,
                           String failureReason, OffsetDateTime requestedAt, OffsetDateTime completedAt) {
    }

    /**
     * Records an AI check request for the task's current submission and returns the stored status.
     * With no provider configured this records "manual_review_required" - an honest "AI did not run".
     */
    public String requestVerification(UUID actorId, UUID taskId) {
        if (!provider.isConfigured()) {
            record(actorId, taskId, "manual_review_required", null, null, List.of(), null, null, null,
                    "AI provider is not configured; a human must review this submission.");
            return "manual_review_required";
        }
        List<String> paths = db.run(actorId, j -> j.sql("""
                SELECT e.storage_path FROM app.task_evidence e JOIN app.tasks t ON t.id = e.task_id
                 WHERE e.task_id = :t AND e.submission_no = t.submission_no AND e.status = 'uploaded' ORDER BY e.uploaded_at
                """).param("t", taskId).query(String.class).list());
        int submission = db.run(actorId, j -> j.sql("SELECT submission_no FROM app.tasks WHERE id = :t").param("t", taskId).query(Integer.class).single());
        List<String> labels = db.run(actorId, j -> j.sql("""
                SELECT ci.label FROM app.checklist_items ci JOIN app.tasks t ON t.checklist_version_id = ci.version_id
                 WHERE t.id = :t ORDER BY ci.position
                """).param("t", taskId).query(String.class).list());
        try {
            // The provider call happens OUTSIDE any database transaction.
            AiVerificationProvider.AiOutcome o = provider.verify(new AiVerificationProvider.AiRequest(taskId, submission, paths, labels));
            record(actorId, taskId, "completed", o.recommendation(), o.confidence(), o.reasonCodes(), o.summary(),
                    provider.providerName(), o.modelVersion(), null);
            return "completed";
        } catch (Exception e) {
            record(actorId, taskId, "failed", null, null, List.of(), null, provider.providerName(), null,
                    "AI provider call failed; a human must review this submission.");
            return "failed";
        }
    }

    private void record(UUID actorId, UUID taskId, String status, String rec, BigDecimal conf, List<String> reasons,
                        String summary, String providerName, String model, String failure) {
        db.runVoid(actorId, j -> j.sql("""
                SELECT app.record_ai_result(:t, CAST(:status AS app.ai_status), CAST(:rec AS app.ai_recommendation), :conf,
                                            CAST(:reasons AS text[]), :summary, :provider, :model, :failure)
                """).param("t", taskId).param("status", status).param("rec", rec).param("conf", conf)
                .param("reasons", Pg.textArray(reasons)).param("summary", summary).param("provider", providerName)
                .param("model", model).param("failure", failure).query((rs, n) -> 1).list());
    }

    /** Latest AI result for the task's current submission, or null if none was ever requested. */
    public static AiResult latest(JdbcClient j, UUID taskId) {
        return j.sql("""
                SELECT a.id, a.submission_no, a.status::text AS status, a.recommendation::text AS recommendation, a.confidence,
                       a.reason_codes, a.summary, a.provider, a.model_version, a.failure_reason, a.requested_at, a.completed_at
                  FROM app.ai_verification_results a JOIN app.tasks t ON t.id = a.task_id AND a.submission_no = t.submission_no
                 WHERE a.task_id = :t ORDER BY a.requested_at DESC LIMIT 1
                """).param("t", taskId)
                .query((rs, n) -> new AiResult(Rs.uuid(rs, "id"), rs.getInt("submission_no"), rs.getString("status"),
                        rs.getString("recommendation"), rs.getBigDecimal("confidence"), Pg.toList(rs.getArray("reason_codes")),
                        rs.getString("summary"), rs.getString("provider"), rs.getString("model_version"),
                        rs.getString("failure_reason"), Rs.ts(rs, "requested_at"), Rs.ts(rs, "completed_at")))
                .optional().orElse(null);
    }
}
