package com.chasehorse.erp.task;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class TaskDtos {
    private TaskDtos() {
    }

    /** Money is integer paise. assignee / reward fields are null for callers who may not see them (customers, partners). */
    public record TaskDto(UUID id, long taskNo, String title, String description, String category, String priority,
                          String status, UUID customerOrgId, String customerName, UUID partnerOrgId, String address,
                          String city, String state, String postalCode, double lat, double lng, int completionRadiusM,
                          Long rewardPaise, OffsetDateTime availableFrom, OffsetDateTime dueAt, List<String> requiredSkills,
                          UUID checklistVersionId, UUID assigneeId, String assigneeName, OffsetDateTime acceptedAt,
                          int submissionNo, OffsetDateTime submittedAt, long version, OffsetDateTime createdAt,
                          OffsetDateTime updatedAt) {
    }

    public record NearbyTask(TaskDto task, double distanceKm) {
    }

    public record CreateTaskRequest(@NotBlank @Size(min = 3, max = 200) String title,
                                    @Size(max = 5000) String description,
                                    @NotBlank @Size(max = 80) String category,
                                    @Pattern(regexp = "low|normal|high|urgent") String priority,
                                    @NotNull UUID customerOrgId, UUID partnerOrgId,
                                    @Size(max = 500) String address, @Size(max = 120) String city,
                                    @Size(max = 120) String state, @Size(max = 16) String postalCode,
                                    @NotNull @DecimalMin("-90") @DecimalMax("90") BigDecimal lat,
                                    @NotNull @DecimalMin("-180") @DecimalMax("180") BigDecimal lng,
                                    @Min(10) @Max(5000) Integer completionRadiusM,
                                    @Min(0) Long rewardPaise,
                                    OffsetDateTime availableFrom, OffsetDateTime dueAt,
                                    @Size(max = 20) List<@Size(max = 60) String> requiredSkills,
                                    @NotNull UUID checklistVersionId) {
    }

    public record AssignRequest(@NotNull UUID employeeId) {
    }

    public record ReasonRequest(@NotBlank @Size(min = 3, max = 500) String reason) {
    }

    public record OptionalReasonRequest(@Size(max = 500) String reason) {
    }

    public record RewardRequest(@NotNull @Min(0) Long rewardPaise) {
    }

    public record SubmissionResult(UUID taskId, int submissionNo, String aiVerificationStatus) {
    }

    public record HistoryEntry(UUID id, String fromStatus, String toStatus, UUID actorId, String actorName, OffsetDateTime occurredAt) {
    }
}
