package com.chasehorse.erp.checklist;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import com.chasehorse.erp.checklist.ChecklistDtos.*;
import com.chasehorse.erp.common.ApiException;
import com.chasehorse.erp.common.Rs;
import com.chasehorse.erp.db.ActorDb;
import com.chasehorse.erp.security.Actor;
import com.chasehorse.erp.security.AppRole;
import com.chasehorse.erp.task.TaskService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class ChecklistService {
    private final ActorDb db;
    private final ObjectMapper mapper;

    public ChecklistService(ActorDb db, ObjectMapper mapper) {
        this.db = db;
        this.mapper = mapper;
    }

    public List<Template> list(Actor actor) {
        actor.requireStaff();
        return db.run(actor.id(), j -> {
            List<Template> heads = j.sql(HEAD + " ORDER BY t.name").query((rs, n) -> head(rs)).list();
            return heads.stream().map(h -> withItems(j, h)).toList();
        });
    }

    public Template get(Actor actor, UUID id) {
        actor.requireStaff();
        return db.run(actor.id(), j -> loadTemplate(j, id));
    }

    public Template save(Actor actor, UUID templateId, SaveTemplateRequest r) {
        actor.requireAny(AppRole.ADMIN, AppRole.MANAGER);
        String json;
        try {
            json = mapper.writeValueAsString(r.items().stream()
                    .map(i -> Map.of("label", i.label().trim(), "required", i.required(), "requiresPhoto", i.requiresPhoto()))
                    .toList());
        } catch (JsonProcessingException e) {
            throw ApiException.badRequest("Items could not be read.");
        }
        return db.run(actor.id(), j -> {
            UUID versionId = j.sql("SELECT app.save_checklist_template(:id, :name, :cat, CAST(:items AS jsonb))")
                    .param("id", templateId).param("name", r.name().trim()).param("cat", r.category().trim()).param("items", json)
                    .query(UUID.class).single();
            UUID tpl = j.sql("SELECT template_id FROM app.checklist_template_versions WHERE id = :v").param("v", versionId)
                    .query(UUID.class).single();
            return loadTemplate(j, tpl);
        });
    }

    public List<TaskChecklistItem> forTask(Actor actor, UUID taskId) {
        actor.requireActive();
        return db.run(actor.id(), j -> {
            TaskService.load(j, actor, taskId); // 404 unless the caller may see the task
            return j.sql("""
                    SELECT ci.id, ci.position, ci.label, ci.required, ci.requires_photo,
                           coalesce(cr.state::text, 'pending') AS state, cr.note, cr.na_reason,
                           (SELECT count(*) FROM app.task_evidence e
                             WHERE e.task_id = t.id AND e.checklist_item_id = ci.id AND e.status = 'uploaded'
                               AND e.submission_no = CASE WHEN t.status IN ('in_progress', 'rework_requested')
                                                          THEN t.submission_no + 1 ELSE t.submission_no END) AS photos
                      FROM app.tasks t
                      JOIN app.checklist_items ci ON ci.version_id = t.checklist_version_id
                      LEFT JOIN app.checklist_responses cr ON cr.task_id = t.id AND cr.item_id = ci.id
                     WHERE t.id = :t ORDER BY ci.position
                    """).param("t", taskId)
                    .query((rs, n) -> new TaskChecklistItem(Rs.uuid(rs, "id"), rs.getInt("position"), rs.getString("label"),
                            rs.getBoolean("required"), rs.getBoolean("requires_photo"), rs.getString("state"),
                            rs.getString("note"), rs.getString("na_reason"), rs.getLong("photos")))
                    .list();
        });
    }

    public void respond(Actor actor, UUID taskId, UUID itemId, ResponseRequest r) {
        actor.requireAny(AppRole.EMPLOYEE);
        db.runVoid(actor.id(), j -> j.sql("SELECT app.save_checklist_response(:t, :i, CAST(:s AS app.checklist_state), :note, :na)")
                .param("t", taskId).param("i", itemId).param("s", r.state()).param("note", r.note()).param("na", r.naReason())
                .query((rs, n) -> 1).list());
    }

    // ---- helpers --------------------------------------------------------------

    private static final String HEAD = """
            SELECT t.id, t.name, t.category, t.active, v.id AS version_id, v.version, v.created_at
              FROM app.checklist_templates t
              JOIN LATERAL (SELECT * FROM app.checklist_template_versions WHERE template_id = t.id ORDER BY version DESC LIMIT 1) v ON true
            """;

    private record Head(UUID id, String name, String category, boolean active, UUID versionId, int version, java.time.OffsetDateTime at) {
    }

    private static Head head(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Head(Rs.uuid(rs, "id"), rs.getString("name"), rs.getString("category"), rs.getBoolean("active"),
                Rs.uuid(rs, "version_id"), rs.getInt("version"), Rs.ts(rs, "created_at"));
    }

    private static Template withItems(JdbcClient j, Head h) {
        List<Item> items = j.sql("SELECT id, position, label, required, requires_photo FROM app.checklist_items WHERE version_id = :v ORDER BY position")
                .param("v", h.versionId())
                .query((rs, n) -> new Item(Rs.uuid(rs, "id"), rs.getInt("position"), rs.getString("label"), rs.getBoolean("required"), rs.getBoolean("requires_photo")))
                .list();
        return new Template(h.id(), h.name(), h.category(), h.active(), h.versionId(), h.version(), h.at(), items);
    }

    private static Template loadTemplate(JdbcClient j, UUID id) {
        Head h = j.sql(HEAD + " WHERE t.id = :id").param("id", id).query((rs, n) -> head(rs)).optional()
                .orElseThrow(() -> ApiException.notFound("Checklist template not found."));
        return withItems(j, h);
    }
}
