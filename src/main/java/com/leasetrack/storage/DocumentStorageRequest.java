package com.leasetrack.storage;

import java.io.InputStream;
import java.util.UUID;

public record DocumentStorageRequest(
        UUID noticeId,
        UUID deliveryAttemptId,
        UUID documentId,
        String originalFilename,
        String contentType,
        InputStream content) {
}
