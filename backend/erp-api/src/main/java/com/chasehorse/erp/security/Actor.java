package com.chasehorse.erp.security;

import java.util.Arrays;
import java.util.Set;
import java.util.UUID;

import com.chasehorse.erp.common.ApiException;

/**
 * The authenticated caller, as the DATABASE knows them. Roles and status are
 * always loaded from app.user_roles / app.profiles on each request - never taken
 * from the token - so a suspension or role change takes effect immediately.
 * Roles only count while the account is ACTIVE.
 */
public record Actor(UUID id, String email, AccountStatus status, Set<AppRole> roles) {

    public boolean isActive() {
        return status == AccountStatus.ACTIVE;
    }

    public boolean has(AppRole role) {
        return isActive() && roles.contains(role);
    }

    public boolean hasAny(AppRole... candidates) {
        return Arrays.stream(candidates).anyMatch(this::has);
    }

    /** Admin, manager or operations. */
    public boolean isStaff() {
        return hasAny(AppRole.ADMIN, AppRole.MANAGER, AppRole.OPERATIONS);
    }

    public Actor requireActive() {
        if (!isActive()) {
            throw ApiException.forbidden("Your account is not active (status: " + status.name().toLowerCase() + ").");
        }
        return this;
    }

    public Actor requireAny(AppRole... allowed) {
        requireActive();
        if (!hasAny(allowed)) {
            throw ApiException.forbidden("Your role is not permitted to perform this action.");
        }
        return this;
    }

    public Actor requireStaff() {
        return requireAny(AppRole.ADMIN, AppRole.MANAGER, AppRole.OPERATIONS);
    }
}
