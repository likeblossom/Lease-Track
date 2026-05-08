package com.leasetrack.storage;

import com.leasetrack.exception.FileStorageException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@ConditionalOnProperty(name = "app.storage.provider", havingValue = "local", matchIfMissing = true)
public class LocalDocumentStorageService implements DocumentStorageService {

    private final Path storageRoot;

    public LocalDocumentStorageService(@Value("${app.storage.local.root}") String storageRoot) {
        this.storageRoot = Path.of(storageRoot).toAbsolutePath().normalize();
    }

    @Override
    public StoredDocument store(DocumentStorageRequest request) {
        String sanitizedFilename = sanitizeFilename(request.originalFilename());
        String storageKey = "%s/%s/%s-%s".formatted(
                request.noticeId(),
                request.deliveryAttemptId(),
                request.documentId(),
                sanitizedFilename);
        Path destination = storageRoot.resolve(storageKey).normalize();

        if (!destination.startsWith(storageRoot)) {
            throw new FileStorageException("Invalid storage destination");
        }

        try {
            Files.createDirectories(destination.getParent());
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long sizeBytes;
            try (InputStream input = request.content();
                    DigestInputStream digestInput = new DigestInputStream(input, digest)) {
                sizeBytes = Files.copy(digestInput, destination);
            }
            return new StoredDocument(
                    "local",
                    storageKey,
                    HexFormat.of().formatHex(digest.digest()),
                    sizeBytes);
        } catch (IOException | NoSuchAlgorithmException ex) {
            throw new FileStorageException("Failed to store evidence document", ex);
        }
    }

    private String sanitizeFilename(String filename) {
        String cleaned = StringUtils.cleanPath(filename == null ? "document" : filename);
        cleaned = cleaned.replaceAll("[^A-Za-z0-9._-]", "_");
        return cleaned.isBlank() || cleaned.contains("..") ? "document" : cleaned;
    }
}
