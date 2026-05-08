package com.leasetrack.service;

import com.leasetrack.domain.entity.DeliveryAttempt;
import com.leasetrack.domain.entity.DeliveryEvidence;
import com.leasetrack.domain.entity.EvidenceDocument;
import com.leasetrack.domain.entity.Notice;
import com.leasetrack.domain.entity.User;
import com.leasetrack.domain.enums.EvidenceDocumentType;
import com.leasetrack.domain.enums.EvidenceStrength;
import com.leasetrack.domain.enums.UserRole;
import com.leasetrack.dto.response.EvidenceDocumentResponse;
import com.leasetrack.event.publisher.NoticeEventPublisher;
import com.leasetrack.exception.DeliveryAttemptNotFoundException;
import com.leasetrack.exception.InvalidEvidenceDocumentException;
import com.leasetrack.mapper.NoticeMapper;
import com.leasetrack.repository.DeliveryAttemptRepository;
import com.leasetrack.repository.DeliveryEvidenceRepository;
import com.leasetrack.repository.EvidenceDocumentRepository;
import com.leasetrack.security.CurrentUserService;
import com.leasetrack.storage.DocumentStorageRequest;
import com.leasetrack.storage.DocumentStorageService;
import com.leasetrack.storage.StoredDocument;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class EvidenceDocumentService {

    private final DeliveryAttemptRepository deliveryAttemptRepository;
    private final DeliveryEvidenceRepository deliveryEvidenceRepository;
    private final EvidenceDocumentRepository evidenceDocumentRepository;
    private final DocumentStorageService documentStorageService;
    private final EvidenceStrengthService evidenceStrengthService;
    private final AuditService auditService;
    private final NoticeEventPublisher noticeEventPublisher;
    private final NoticeMapper noticeMapper;
    private final CurrentUserService currentUserService;
    private final Clock clock;

    public EvidenceDocumentService(
            DeliveryAttemptRepository deliveryAttemptRepository,
            DeliveryEvidenceRepository deliveryEvidenceRepository,
            EvidenceDocumentRepository evidenceDocumentRepository,
            DocumentStorageService documentStorageService,
            EvidenceStrengthService evidenceStrengthService,
            AuditService auditService,
            NoticeEventPublisher noticeEventPublisher,
            NoticeMapper noticeMapper,
            CurrentUserService currentUserService,
            Clock clock) {
        this.deliveryAttemptRepository = deliveryAttemptRepository;
        this.deliveryEvidenceRepository = deliveryEvidenceRepository;
        this.evidenceDocumentRepository = evidenceDocumentRepository;
        this.documentStorageService = documentStorageService;
        this.evidenceStrengthService = evidenceStrengthService;
        this.auditService = auditService;
        this.noticeEventPublisher = noticeEventPublisher;
        this.noticeMapper = noticeMapper;
        this.currentUserService = currentUserService;
        this.clock = clock;
    }

    @Transactional
    public EvidenceDocumentResponse uploadEvidenceDocument(
            UUID noticeId,
            UUID deliveryAttemptId,
            EvidenceDocumentType documentType,
            MultipartFile file) {
        validateUpload(documentType, file);

        DeliveryAttempt attempt = deliveryAttemptRepository.findByIdAndNotice_Id(deliveryAttemptId, noticeId)
                .orElseThrow(() -> new DeliveryAttemptNotFoundException(noticeId, deliveryAttemptId));
        User currentUser = currentUserService.currentUser();
        assertCanManage(currentUser, attempt.getNotice());

        Instant now = Instant.now(clock);
        DeliveryEvidence evidence = deliveryEvidenceRepository.findByDeliveryAttempt_Id(deliveryAttemptId)
                .orElseGet(() -> newEvidence(attempt, now));

        UUID documentId = UUID.randomUUID();
        StoredDocument storedDocument = storeFile(noticeId, deliveryAttemptId, documentId, file);
        applyDocumentReference(evidence, documentType, storedDocument.storageKey());
        evidence.setUpdatedAt(now);
        attempt.setEvidence(evidence);
        DeliveryEvidence savedEvidence = deliveryEvidenceRepository.save(evidence);

        EvidenceDocument document = new EvidenceDocument();
        document.setId(documentId);
        document.setNoticeId(noticeId);
        document.setDeliveryAttempt(attempt);
        document.setDeliveryEvidence(savedEvidence);
        document.setDocumentType(documentType);
        document.setOriginalFilename(originalFilename(file));
        document.setContentType(file.getContentType());
        document.setSizeBytes(storedDocument.sizeBytes());
        document.setStorageProvider(storedDocument.storageProvider());
        document.setStorageKey(storedDocument.storageKey());
        document.setSha256Checksum(storedDocument.sha256Checksum());
        document.setUploadedByUserId(currentUser.getId());
        document.setCreatedAt(now);

        EvidenceDocument savedDocument = evidenceDocumentRepository.save(document);
        EvidenceStrength evidenceStrength = evidenceStrengthService.classify(attempt, savedEvidence);
        auditService.recordEvidenceDocumentUploaded(savedDocument);
        noticeEventPublisher.publishEvidenceUploaded(savedEvidence, evidenceStrength);
        return noticeMapper.toResponse(savedDocument);
    }

    @Transactional(readOnly = true)
    public List<EvidenceDocumentResponse> listEvidenceDocuments(UUID noticeId, UUID deliveryAttemptId) {
        DeliveryAttempt attempt = deliveryAttemptRepository.findByIdAndNotice_Id(deliveryAttemptId, noticeId)
                .orElseThrow(() -> new DeliveryAttemptNotFoundException(noticeId, deliveryAttemptId));
        assertCanRead(currentUserService.currentUser(), attempt.getNotice());

        return evidenceDocumentRepository.findByDeliveryAttempt_IdOrderByCreatedAtAsc(deliveryAttemptId).stream()
                .map(noticeMapper::toResponse)
                .toList();
    }

    private DeliveryEvidence newEvidence(DeliveryAttempt attempt, Instant now) {
        DeliveryEvidence evidence = new DeliveryEvidence();
        evidence.setId(UUID.randomUUID());
        evidence.setDeliveryAttempt(attempt);
        evidence.setCreatedAt(now);
        return evidence;
    }

    private StoredDocument storeFile(UUID noticeId, UUID deliveryAttemptId, UUID documentId, MultipartFile file) {
        try {
            return documentStorageService.store(new DocumentStorageRequest(
                    noticeId,
                    deliveryAttemptId,
                    documentId,
                    originalFilename(file),
                    file.getContentType(),
                    file.getInputStream()));
        } catch (IOException ex) {
            throw new InvalidEvidenceDocumentException("Unable to read uploaded file");
        }
    }

    private void applyDocumentReference(
            DeliveryEvidence evidence,
            EvidenceDocumentType documentType,
            String storageKey) {
        switch (documentType) {
            case CARRIER_RECEIPT -> evidence.setCarrierReceiptRef(storageKey);
            case DELIVERY_CONFIRMATION -> {
                evidence.setDeliveryConfirmation(true);
                evidence.setDeliveryConfirmationMetadata(storageKey);
            }
            case SIGNED_ACKNOWLEDGEMENT -> evidence.setSignedAcknowledgementRef(storageKey);
            case EMAIL_ACKNOWLEDGEMENT -> evidence.setEmailAcknowledgementRef(storageKey);
            case BAILIFF_AFFIDAVIT -> evidence.setBailiffAffidavitRef(storageKey);
            case OTHER -> {
            }
        }
    }

    private void validateUpload(EvidenceDocumentType documentType, MultipartFile file) {
        if (documentType == null) {
            throw new InvalidEvidenceDocumentException("documentType is required");
        }
        if (file == null || file.isEmpty()) {
            throw new InvalidEvidenceDocumentException("Uploaded file must not be empty");
        }
        if (!StringUtils.hasText(originalFilename(file))) {
            throw new InvalidEvidenceDocumentException("Uploaded file must have a filename");
        }
    }

    private String originalFilename(MultipartFile file) {
        return file.getOriginalFilename() == null ? "document" : file.getOriginalFilename();
    }

    private void assertCanRead(User user, Notice notice) {
        if (user.getRole() == UserRole.ADMIN
                || user.getId().equals(notice.getOwnerUserId())
                || user.getId().equals(notice.getTenantUserId())) {
            return;
        }
        throw new AccessDeniedException("User cannot access this notice");
    }

    private void assertCanManage(User user, Notice notice) {
        if (user.getRole() == UserRole.ADMIN) {
            return;
        }
        if ((user.getRole() == UserRole.LANDLORD || user.getRole() == UserRole.PROPERTY_MANAGER)
                && user.getId().equals(notice.getOwnerUserId())) {
            return;
        }
        throw new AccessDeniedException("User cannot manage this notice");
    }
}
