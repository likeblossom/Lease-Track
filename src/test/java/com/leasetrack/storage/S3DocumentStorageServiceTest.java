package com.leasetrack.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

@ExtendWith(MockitoExtension.class)
class S3DocumentStorageServiceTest {

    @Mock
    private S3Client s3Client;

    @Test
    void storesDocumentInS3WithMetadataAndChecksum() throws Exception {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().eTag("etag").build());
        UUID noticeId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        byte[] content = "carrier receipt".getBytes(StandardCharsets.UTF_8);
        S3DocumentStorageService service = new S3DocumentStorageService(s3Client, "lease-track-prod", "/prod/evidence/");

        StoredDocument storedDocument = service.store(new DocumentStorageRequest(
                noticeId,
                attemptId,
                documentId,
                "Receipt 1.pdf",
                "application/pdf",
                new ByteArrayInputStream(content)));

        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(requestCaptor.capture(), any(RequestBody.class));
        PutObjectRequest putRequest = requestCaptor.getValue();
        String expectedChecksum = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));

        assertThat(storedDocument.storageProvider()).isEqualTo("s3");
        assertThat(storedDocument.storageKey())
                .isEqualTo("prod/evidence/" + noticeId + "/" + attemptId + "/" + documentId + "-Receipt_1.pdf");
        assertThat(storedDocument.sha256Checksum()).isEqualTo(expectedChecksum);
        assertThat(storedDocument.sizeBytes()).isEqualTo(content.length);
        assertThat(putRequest.bucket()).isEqualTo("lease-track-prod");
        assertThat(putRequest.key()).isEqualTo(storedDocument.storageKey());
        assertThat(putRequest.contentType()).isEqualTo("application/pdf");
        assertThat(putRequest.contentLength()).isEqualTo(content.length);
        assertThat(putRequest.metadata()).containsEntry("sha256", expectedChecksum);
        assertThat(putRequest.metadata()).containsEntry("notice-id", noticeId.toString());
        assertThat(putRequest.metadata()).containsEntry("delivery-attempt-id", attemptId.toString());
        assertThat(putRequest.metadata()).containsEntry("document-id", documentId.toString());
    }

    @Test
    void failsFastWhenBucketIsMissing() {
        S3DocumentStorageService service = new S3DocumentStorageService(s3Client, " ", "evidence");

        assertThatThrownBy(service::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.storage.s3.bucket");
    }
}
