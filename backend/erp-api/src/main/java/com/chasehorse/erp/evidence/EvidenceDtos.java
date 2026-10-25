package com.chasehorse.erp.evidence;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public final class EvidenceDtos {
    private EvidenceDtos() {
    }

    /** Fields prefixed "claimed" are what the device REPORTED. They are context for a reviewer, never proof. */
    public record BeginUploadRequest(UUID checklistItemId,
                                     @NotBlank @Pattern(regexp = "image/(jpeg|png|webp|heic)") String mimeType,
                                     @Max(15_728_640L) Long sizeBytes,
                                     OffsetDateTime claimedCapturedAt,
                                     @DecimalMin("-90") @DecimalMax("90") BigDecimal claimedLat,
                                     @DecimalMin("-180") @DecimalMax("180") BigDecimal claimedLng,
                                     @DecimalMin("0") @DecimalMax("100000") BigDecimal claimedAccuracyM) {
    }

    public record BeginUploadResponse(UUID evidenceId, String storagePath, String uploadUrl, String uploadToken,
                                      int expiresInSeconds, long maxBytes) {
    }

    public record Evidence(UUID id, UUID taskId, UUID checklistItemId, UUID uploadedBy, int submissionNo, String mimeType,
                           Long sizeBytes, String sha256, String status, OffsetDateTime claimedCapturedAt,
                           Double claimedLat, Double claimedLng, Double claimedAccuracyM, Integer distanceToTaskM,
                           OffsetDateTime serverReceivedAt, OffsetDateTime uploadedAt) {
    }

    public record SignedUrl(String url, int expiresInSeconds) {
    }
}
