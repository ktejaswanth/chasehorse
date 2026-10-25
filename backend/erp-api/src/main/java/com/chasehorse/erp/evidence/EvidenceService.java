package com.chasehorse.erp.evidence;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import com.chasehorse.erp.common.ApiException;
import com.chasehorse.erp.common.Rs;
import com.chasehorse.erp.config.AppProperties;
import com.chasehorse.erp.db.ActorDb;
import com.chasehorse.erp.evidence.EvidenceDtos.*;
import com.chasehorse.erp.security.Actor;
import com.chasehorse.erp.security.AppRole;
import com.chasehorse.erp.storage.StorageService;
import com.chasehorse.erp.task.TaskService;

@Service
public class EvidenceService {
    public static final long MAX_BYTES = 15L * 1024 * 1024;

    private final ActorDb db;
    private final StorageService storage;
    private final int ttl;

    public EvidenceService(ActorDb db, StorageService storage, AppProperties props) {
        this.db = db;
        this.storage = storage;
        this.ttl = props.supabase().signedUrlSeconds() > 0 ? props.supabase().signedUrlSeconds() : 600;
    }

    /** Step 1: reserve a metadata row + server-generated path, then hand out a short-lived signed upload URL. */
    public BeginUploadResponse begin(Actor actor, UUID taskId, BeginUploadRequest r) {
        actor.requireAny(AppRole.EMPLOYEE);
        if (!storage.isConfigured()) {
            throw ApiException.unavailable("File storage is not configured on this server.");
        }
        record Reserved(UUID id, String path) {
        }
        Reserved res = db.run(actor.id(), j -> j.sql("SELECT evidence_id, storage_path FROM app.begin_evidence_upload(:t, :i, :m, :at, :lat, :lng, :acc)")
                .param("t", taskId).param("i", r.checklistItemId()).param("m", r.mimeType())
                .param("at", r.claimedCapturedAt()).param("lat", r.claimedLat()).param("lng", r.claimedLng())
                .param("acc", r.claimedAccuracyM())
                .query((rs, n) -> new Reserved(Rs.uuid(rs, "evidence_id"), rs.getString("storage_path"))).single());
        StorageService.SignedUpload up = storage.createSignedUpload(res.path());
        return new BeginUploadResponse(res.id(), res.path(), up.uploadUrl(), up.token(), up.expiresInSeconds(), MAX_BYTES);
    }

    /**
     * Step 2: after the client uploaded, the SERVER reads the object, measures its size and SHA-256, checks that
     * the real bytes are the declared image type, and only then marks the evidence as uploaded.
     */
    public Evidence confirm(Actor actor, UUID evidenceId) {
        actor.requireAny(AppRole.EMPLOYEE);
        record Pending(String path, String mime) {
        }
        Pending p = db.run(actor.id(), j -> j.sql("SELECT storage_path, mime_type FROM app.task_evidence WHERE id = :id AND uploaded_by = :me AND status = 'pending_upload'")
                .param("id", evidenceId).param("me", actor.id())
                .query((rs, n) -> new Pending(rs.getString("storage_path"), rs.getString("mime_type"))).optional()
                .orElseThrow(() -> ApiException.notFound("Pending evidence not found.")));

        StorageService.ObjectFacts facts = storage.inspect(p.path(), MAX_BYTES);
        if (!FileSignature.matches(p.mime(), facts.head())) {
            storage.delete(p.path());
            throw ApiException.invalid("The uploaded file is not a valid " + p.mime() + " image.");
        }
        return db.run(actor.id(), j -> {
            ActorDb.call(j, "SELECT app.confirm_evidence_upload(:id, :size, :sha)",
                    Map.of("id", evidenceId, "size", facts.sizeBytes(), "sha", facts.sha256()));
            return one(j, evidenceId);
        });
    }

    /** Evidence metadata for a task the caller may see. Customers see none (evidence is internal). */
    public List<Evidence> forTask(Actor actor, UUID taskId) {
        actor.requireActive();
        if (!actor.isStaff() && !actor.has(AppRole.EMPLOYEE)) {
            throw ApiException.forbidden("Evidence is not available to your role.");
        }
        return db.run(actor.id(), j -> {
            TaskService.load(j, actor, taskId);
            return j.sql(SELECT + " WHERE e.task_id = :t ORDER BY e.submission_no, e.server_received_at").param("t", taskId)
                    .query((rs, n) -> map(rs)).list();
        });
    }

    public SignedUrl downloadUrl(Actor actor, UUID evidenceId) {
        actor.requireActive();
        String path = db.run(actor.id(), j -> {
            Evidence e = one(j, evidenceId);
            TaskService.load(j, actor, e.taskId());
            if (!actor.isStaff() && !actor.id().equals(e.uploadedBy())) {
                throw ApiException.forbidden("You may not view this file.");
            }
            if (!"uploaded".equals(e.status())) {
                throw ApiException.conflict("The file has not been uploaded.");
            }
            return j.sql("SELECT storage_path FROM app.task_evidence WHERE id = :id").param("id", evidenceId).query(String.class).single();
        });
        return new SignedUrl(storage.createSignedDownloadUrl(path, ttl), ttl);
    }

    // ---- helpers --------------------------------------------------------------

    static final String SELECT = """
            SELECT e.id, e.task_id, e.checklist_item_id, e.uploaded_by, e.submission_no, e.mime_type, e.size_bytes, e.sha256,
                   e.status::text AS status, e.claimed_captured_at, e.claimed_lat, e.claimed_lng, e.claimed_accuracy_m,
                   e.distance_to_task_m, e.server_received_at, e.uploaded_at
              FROM app.task_evidence e
            """;

    static Evidence map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Evidence(Rs.uuid(rs, "id"), Rs.uuid(rs, "task_id"), Rs.uuid(rs, "checklist_item_id"), Rs.uuid(rs, "uploaded_by"),
                rs.getInt("submission_no"), rs.getString("mime_type"), Rs.lng(rs, "size_bytes"), rs.getString("sha256"),
                rs.getString("status"), Rs.ts(rs, "claimed_captured_at"), Rs.dbl(rs, "claimed_lat"), Rs.dbl(rs, "claimed_lng"),
                Rs.dbl(rs, "claimed_accuracy_m"), Rs.integer(rs, "distance_to_task_m"), Rs.ts(rs, "server_received_at"),
                Rs.ts(rs, "uploaded_at"));
    }

    static Evidence one(JdbcClient j, UUID id) {
        return j.sql(SELECT + " WHERE e.id = :id").param("id", id).query((rs, n) -> map(rs)).optional()
                .orElseThrow(() -> ApiException.notFound("Evidence not found."));
    }
}
