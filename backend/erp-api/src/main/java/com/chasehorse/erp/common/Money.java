package com.chasehorse.erp.common;

/** Money is stored and transported as integer paise (INR only). Never floating point. */
public final class Money {
    public static final long MAX_PAISE = 100_000_000_00L; // INR 100,000,000 sanity cap per task reward

    private Money() {
    }

    public static void requireValidReward(long paise) {
        if (paise < 0 || paise > MAX_PAISE) {
            throw ApiException.invalid("rewardPaise must be between 0 and " + MAX_PAISE);
        }
    }
}
