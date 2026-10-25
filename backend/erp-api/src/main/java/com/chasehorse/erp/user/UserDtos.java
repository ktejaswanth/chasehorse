package com.chasehorse.erp.user;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class UserDtos {
    private UserDtos() {
    }

    public record Me(UUID id, String email, String displayName, String phone, String status, List<String> roles,
                     boolean registered, boolean locationConsent) {
    }

    public record RegisterRequest(@NotBlank @Size(max = 200) String displayName,
                                  @Size(max = 32) String phone,
                                  @Size(max = 120) String region) {
    }

    public record ConsentRequest(@NotNull Boolean consent) {
    }

    public record LocationRequest(@NotNull @DecimalMin("-90") @DecimalMax("90") BigDecimal lat,
                                  @NotNull @DecimalMin("-180") @DecimalMax("180") BigDecimal lng,
                                  @DecimalMin("0") @DecimalMax("100000") BigDecimal accuracyM,
                                  OffsetDateTime capturedAt) {
    }

    public record EmployeeSummary(UUID id, String email, String displayName, String phone, String region,
                                  List<String> skills, String accountStatus, boolean onTask, long tasksCompleted,
                                  int searchRadiusKm, Double lastLat, Double lastLng, OffsetDateTime lastLocationAt,
                                  OffsetDateTime joinedAt) {
    }

    public record AccountDecisionRequest(@NotBlank @Pattern(regexp = "approved|rejected|suspended|reactivated") String decision,
                                         @Size(max = 1000) String reason) {
    }

    public record AccountDecisionResponse(UUID userId, String accountStatus) {
    }

    public record ProvisionRequest(@NotNull UUID userId, @NotBlank @Email @Size(max = 320) String email,
                                   @NotBlank @Size(max = 200) String displayName,
                                   @NotBlank @Pattern(regexp = "admin|manager|operations|employee|customer|partner") String role,
                                   UUID organizationId) {
    }

    public record Organization(UUID id, String name, String kind, boolean active) {
    }

    public record NewOrganization(@NotBlank @Size(max = 200) String name,
                                  @NotBlank @Pattern(regexp = "internal|customer|partner") String kind) {
    }

    public record AccountRow(UUID id, String email, String displayName, String accountStatus, List<String> roles,
                             OffsetDateTime createdAt) {
    }
}
