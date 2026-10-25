package com.chasehorse.erp.storage;

/**
 * Private object storage for evidence files. The bucket is private; clients never get a permanent
 * URL. They upload with a short-lived signed URL and staff view with a short-lived signed URL, both
 * created here after the API's own authorisation checks.
 */
public interface StorageService {

    boolean isConfigured();

    SignedUpload createSignedUpload(String path);

    String createSignedDownloadUrl(String path, int seconds);

    /** Reads the stored object once, measuring size and SHA-256 itself (never trusting the client). */
    ObjectFacts inspect(String path, long maxBytes);

    void delete(String path);

    record SignedUpload(String uploadUrl, String token, int expiresInSeconds) {
    }

    record ObjectFacts(long sizeBytes, String sha256, byte[] head) {
    }
}
