package com.leasetrack.storage;

import com.leasetrack.exception.FileStorageException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Service
@ConditionalOnProperty(name = "app.storage.provider", havingValue = "s3")
public class S3DocumentStorageService implements DocumentStorageService, InitializingBean {

    private final S3Client s3Client;
    private final String bucket;
    private final String prefix;

    public S3DocumentStorageService(
            S3Client s3Client,
            @Value("${app.storage.s3.bucket:}") String bucket,
            @Value("${app.storage.s3.prefix:evidence-documents}") String prefix) {
        this.s3Client = s3Client;
        this.bucket = bucket;
        this.prefix = trimSlashes(prefix == null ? "" : prefix.trim());
    }

    @Override
    public void afterPropertiesSet() {
        if (!StringUtils.hasText(bucket)) {
            throw new IllegalStateException("S3 storage is enabled but app.storage.s3.bucket is not configured");
        }
    }

    @Override
    public StoredDocument store(DocumentStorageRequest request) {
        String sanitizedFilename = sanitizeFilename(request.originalFilename());
        String storageKey = storageKey(request, sanitizedFilename);
        Path tempFile = null;

        try {
            tempFile = Files.createTempFile("lease-track-s3-", ".upload");
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long sizeBytes;
            try (InputStream input = request.content();
                    DigestInputStream digestInput = new DigestInputStream(input, digest)) {
                sizeBytes = Files.copy(digestInput, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }

            String checksum = HexFormat.of().formatHex(digest.digest());
            Map<String, String> metadata = new HashMap<>();
            metadata.put("sha256", checksum);
            metadata.put("notice-id", request.noticeId().toString());
            metadata.put("delivery-attempt-id", request.deliveryAttemptId().toString());
            metadata.put("document-id", request.documentId().toString());

            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(storageKey)
                    .contentType(StringUtils.hasText(request.contentType())
                            ? request.contentType()
                            : "application/octet-stream")
                    .contentLength(sizeBytes)
                    .metadata(metadata)
                    .build();

            s3Client.putObject(putRequest, RequestBody.fromFile(tempFile));
            return new StoredDocument("s3", storageKey, checksum, sizeBytes);
        } catch (IOException | NoSuchAlgorithmException | SdkException ex) {
            throw new FileStorageException("Failed to store evidence document in S3", ex);
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                    // Temporary upload cleanup failure should not hide the storage result.
                }
            }
        }
    }

    private String storageKey(DocumentStorageRequest request, String sanitizedFilename) {
        String key = "%s/%s/%s-%s".formatted(
                request.noticeId(),
                request.deliveryAttemptId(),
                request.documentId(),
                sanitizedFilename);
        return prefix.isBlank() ? key : prefix + "/" + key;
    }

    private String sanitizeFilename(String filename) {
        String cleaned = StringUtils.cleanPath(filename == null ? "document" : filename);
        cleaned = cleaned.replaceAll("[^A-Za-z0-9._-]", "_");
        return cleaned.isBlank() || cleaned.contains("..") ? "document" : cleaned;
    }

    private String trimSlashes(String value) {
        String trimmed = value;
        while (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1);
        }
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
