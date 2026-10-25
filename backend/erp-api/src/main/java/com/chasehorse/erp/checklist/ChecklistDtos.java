package com.chasehorse.erp.checklist;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class ChecklistDtos {
    private ChecklistDtos() {
    }

    public record Item(UUID id, int position, String label, boolean required, boolean requiresPhoto) {
    }

    /** A template with its LATEST version. Versions are immutable; every save creates a new version. */
    public record Template(UUID id, String name, String category, boolean active, UUID versionId, int version,
                           OffsetDateTime updatedAt, List<Item> items) {
    }

    public record ItemInput(@NotBlank @Size(max = 500) String label, boolean required, boolean requiresPhoto) {
    }

    public record SaveTemplateRequest(@NotBlank @Size(max = 200) String name, @NotBlank @Size(max = 80) String category,
                                      @NotEmpty @Size(max = 100) List<@Valid ItemInput> items) {
    }

    public record TaskChecklistItem(UUID itemId, int position, String label, boolean required, boolean requiresPhoto,
                                    String state, String note, String naReason, long photoCount) {
    }

    public record ResponseRequest(@NotNull @Pattern(regexp = "pending|done|not_applicable") String state,
                                  @Size(max = 1000) String note, @Size(max = 500) String naReason) {
    }
}
