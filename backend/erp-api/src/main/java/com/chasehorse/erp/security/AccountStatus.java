package com.chasehorse.erp.security;

import java.util.Locale;

public enum AccountStatus {
    /** Signed in with Supabase but has no ChaseHorse profile yet. */
    UNREGISTERED, PENDING, ACTIVE, SUSPENDED, REJECTED;

    public static AccountStatus fromDb(String value) {
        return valueOf(value.toUpperCase(Locale.ROOT));
    }
}
