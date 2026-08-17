package com.example.lowcode.asset.application;

import java.time.Duration;

public interface StorageGateway {
    String presignPut(String objectKey, String mimeType, Duration expiresIn);

    byte[] readBounded(String objectKey, long maxBytes);

    void promote(String temporaryObjectKey, String finalObjectKey);

    void delete(String objectKey);
}
