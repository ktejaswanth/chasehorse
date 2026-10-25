package com.chasehorse.erp.security;

import java.util.Locale;

public enum AppRole {
    ADMIN, MANAGER, OPERATIONS, EMPLOYEE, CUSTOMER, PARTNER;

    public static AppRole fromDb(String value) {
        return valueOf(value.toUpperCase(Locale.ROOT));
    }

    public String db() {
        return name().toLowerCase(Locale.ROOT);
    }
}
