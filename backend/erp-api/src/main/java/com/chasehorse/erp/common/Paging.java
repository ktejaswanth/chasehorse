package com.chasehorse.erp.common;

/** Bounded offset paging so no endpoint can be asked for an unbounded result set. */
public record Paging(int limit, int offset) {
    public static Paging of(int page, int size, int maxSize) {
        int s = Math.min(Math.max(size, 1), maxSize);
        int p = Math.max(page, 0);
        return new Paging(s, (int) Math.min((long) p * s, Integer.MAX_VALUE));
    }
}
