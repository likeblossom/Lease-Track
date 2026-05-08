package com.leasetrack.storage;

public record StoredDocument(
        String storageProvider,
        String storageKey,
        String sha256Checksum,
        long sizeBytes) {
}
