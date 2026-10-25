package com.chasehorse.erp.storage;

import com.chasehorse.erp.common.ApiException;

/** Used when SUPABASE_URL / SUPABASE_SERVICE_KEY are not set: evidence endpoints answer 503 instead of pretending. */
public class UnconfiguredStorageService implements StorageService {
    private static ApiException off() {
        return ApiException.unavailable("File storage is not configured on this server.");
    }

    @Override
    public boolean isConfigured() {
        return false;
    }

    @Override
    public SignedUpload createSignedUpload(String path) {
        throw off();
    }

    @Override
    public String createSignedDownloadUrl(String path, int seconds) {
        throw off();
    }

    @Override
    public ObjectFacts inspect(String path, long maxBytes) {
        throw off();
    }

    @Override
    public void delete(String path) {
        throw off();
    }
}
