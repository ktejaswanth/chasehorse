package com.chasehorse.erp.dashboard;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.chasehorse.erp.db.ActorDb;
import com.chasehorse.erp.security.Actor;
import com.chasehorse.erp.task.TaskService;

@Service
public class DashboardService {
    private static final List<String> STATUSES = List.of("open", "accepted", "in_progress", "submitted", "rework_requested",
            "manager_approved", "approved", "rejected", "cancelled");

    private final ActorDb db;

    public DashboardService(ActorDb db) {
        this.db = db;
    }

    public record TrendPoint(LocalDate day, long created, long completed) {
    }

    public record Stats(long totalTasks, long openTasks, long inProgress, long awaitingReview, long awaitingOps, long approvedTotal,
                        long activeEmployees, long pendingEmployeeApprovals, Map<String, Long> byStatus, List<TrendPoint> trend) {
    }

    /** Aggregates over the tasks THIS caller may see (customers: their organisations; employees: their own). */
    public Stats stats(Actor actor) {
        actor.requireActive();
        String vis = TaskService.visibility(actor);
        return db.run(actor.id(), j -> {
            Map<String, Long> by = new LinkedHashMap<>();
            STATUSES.forEach(s -> by.put(s, 0L));
            j.sql("SELECT t.status::text AS s, count(*) AS c FROM app.tasks t WHERE " + vis + " GROUP BY t.status")
                    .param("me", actor.id()).query((rs, n) -> {
                        by.put(rs.getString("s"), rs.getLong("c"));
                        return 1;
                    }).list();
            long total = by.values().stream().mapToLong(Long::longValue).sum();

            long active = 0;
            long pending = 0;
            if (actor.isStaff()) {
                active = j.sql("SELECT count(*) FROM app.profiles p JOIN app.user_roles r ON r.user_id = p.id AND r.role = 'employee' WHERE p.account_status = 'active'")
                        .query(Long.class).single();
                pending = j.sql("SELECT count(*) FROM app.profiles p JOIN app.user_roles r ON r.user_id = p.id AND r.role = 'employee' WHERE p.account_status = 'pending'")
                        .query(Long.class).single();
            }

            List<TrendPoint> trend = new ArrayList<>(j.sql("""
                    SELECT d::date AS day,
                           (SELECT count(*) FROM app.tasks t WHERE t.created_at::date = d::date AND %1$s) AS created,
                           (SELECT count(*) FROM app.task_status_history h JOIN app.tasks t ON t.id = h.task_id
                             WHERE h.to_status = 'approved' AND h.occurred_at::date = d::date AND %1$s) AS completed
                      FROM generate_series(current_date - 13, current_date, interval '1 day') d ORDER BY d
                    """.formatted(vis)).param("me", actor.id())
                    .query((rs, n) -> new TrendPoint(rs.getObject("day", LocalDate.class), rs.getLong("created"), rs.getLong("completed")))
                    .list());

            return new Stats(total, by.get("open"),
                    by.get("accepted") + by.get("in_progress") + by.get("rework_requested"),
                    by.get("submitted"), by.get("manager_approved"), by.get("approved"), active, pending, by, trend);
        });
    }
}
