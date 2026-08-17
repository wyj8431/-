package com.example.lowcode.asset.infrastructure;

import com.example.lowcode.asset.application.StorageFailureException;
import com.example.lowcode.asset.application.StorageGateway;
import io.minio.CopyObjectArgs;
import io.minio.CopySource;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.Duration;

@Component
public class MinioStorageGateway implements StorageGateway {
    private final MinioClient minioClient;
    private final String bucket;

    public MinioStorageGateway(
        @Value("${app.minio.endpoint}") String endpoint,
        @Value("${app.minio.access-key}") String accessKey,
        @Value("${app.minio.secret-key}") String secretKey,
        @Value("${app.minio.bucket}") String bucket
    ) {
        this.minioClient = MinioClient.builder()
            .endpoint(endpoint)
            .credentials(accessKey, secretKey)
            .build();
        this.bucket = bucket;
    }

    @Override
    public String presignPut(String objectKey, String mimeType, Duration expiresIn) {
        try {
            return minioClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                    .method(Method.PUT)
                    .bucket(bucket)
                    .object(objectKey)
                    .expiry(Math.toIntExact(expiresIn.toSeconds()))
                    .build()
            );
        } catch (Exception exception) {
            throw new StorageFailureException("Could not presign MinIO upload", exception);
        }
    }

    @Override
    public byte[] readBounded(String objectKey, long maxBytes) {
        if (maxBytes < 1) {
            throw new IllegalArgumentException("maxBytes must be positive");
        }
        try (InputStream input = minioClient.getObject(
            GetObjectArgs.builder().bucket(bucket).object(objectKey).build()
        )) {
            return readBounded(input, maxBytes);
        } catch (Exception exception) {
            throw new StorageFailureException("Could not read MinIO object", exception);
        }
    }

    static byte[] readBounded(InputStream input, long maxBytes) throws java.io.IOException {
        if (maxBytes < 1) {
            throw new IllegalArgumentException("maxBytes must be positive");
        }
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            long remaining = maxBytes;
            while (remaining > 0) {
                int requestedLength = (int) Math.min(buffer.length, remaining);
                int read = input.read(buffer, 0, requestedLength);
                if (read == -1) {
                    break;
                }
                output.write(buffer, 0, read);
                remaining -= read;
            }
            return output.toByteArray();
        }
    }

    @Override
    public void promote(String temporaryObjectKey, String finalObjectKey) {
        try {
            minioClient.copyObject(
                CopyObjectArgs.builder()
                    .bucket(bucket)
                    .object(finalObjectKey)
                    .source(CopySource.builder().bucket(bucket).object(temporaryObjectKey).build())
                    .build()
            );
        } catch (Exception exception) {
            throw new StorageFailureException("Could not promote MinIO upload", exception);
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(objectKey).build());
        } catch (Exception exception) {
            throw new StorageFailureException("Could not delete MinIO object", exception);
        }
    }
}
