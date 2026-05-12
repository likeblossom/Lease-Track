package com.leasetrack.tracking;

public record DeliveryConfirmationCertificate(
        String filename,
        String contentType,
        byte[] content) {
}
