package com.chasehorse.erp.approval;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.chasehorse.erp.ai.AiVerificationService.AiResult;
import com.chasehorse.erp.checklist.ChecklistDtos.TaskChecklistItem;
import com.chasehorse.erp.evidence.EvidenceDtos.Evidence;
import com.chasehorse.erp.task.TaskDtos.TaskDto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class ReviewDtos {
    private ReviewDtos() {
    }

    /** One row of a review queue. The AI fields are advisory and shown separately from human decisions. */
    public record ReviewSummary(UUID taskId, long taskNo, String title, String customerName, UUID employeeId, String employeeName,
                                int submissionNo, OffsetDateTime submittedAt, long rewardPaise, String taskStatus,
                                String aiStatus, String aiRecommendation, String decision, UUID decidedBy,
                                String decidedByName, OffsetDateTime decidedAt, String reason) {
    }

    public record DecisionRow(UUID id, int submissionNo, String stage, String decision, UUID decidedBy, String decidedByName,
                              String reason, UUID aiResultId, OffsetDateTime decidedAt) {
    }

    /** Everything a reviewer needs. "ai" and "decisions" are deliberately separate sections. */
    public record ReviewPackage(TaskDto task, List<Evidence> evidence, List<TaskChecklistItem> checklist, AiResult ai,
                                List<DecisionRow> decisions) {
    }

    public record DecisionRequest(@NotBlank @Pattern(regexp = "manager|operations") String stage,
                                  @NotBlank @Pattern(regexp = "approved|rejected|rework_requested") String decision,
                                  @Size(max = 1000) String reason) {
    }

    public record DecisionResult(UUID taskId, String taskStatus, boolean rewardCredited, UUID ledgerEntryId) {
    }

    public record AiRequestResult(String aiStatus) {
    }
}
