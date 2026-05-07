package com.leasetrack.service;

import com.leasetrack.domain.entity.DeliveryAttempt;
import com.leasetrack.domain.entity.DeliveryEvidence;
import com.leasetrack.domain.enums.DeliveryMethod;
import com.leasetrack.domain.enums.EvidenceStrength;
import org.springframework.stereotype.Service;

@Service
public class EvidenceStrengthService {

    public EvidenceStrength classify(DeliveryAttempt attempt, DeliveryEvidence evidence) {
        if (evidence == null) {
            return EvidenceStrength.WEAK;
        }

        DeliveryMethod method = attempt.getDeliveryMethod();
        return switch (method) {
            case BAILIFF -> hasText(evidence.getBailiffAffidavitRef())
                    ? EvidenceStrength.STRONG
                    : EvidenceStrength.WEAK;
            case HAND_DELIVERY_SIGNATURE -> hasText(evidence.getSignedAcknowledgementRef())
                    ? EvidenceStrength.STRONG
                    : EvidenceStrength.WEAK;
            case REGISTERED_MAIL -> hasText(evidence.getTrackingNumber())
                    && Boolean.TRUE.equals(evidence.getDeliveryConfirmation())
                    ? EvidenceStrength.STRONG
                    : EvidenceStrength.WEAK;
            case EMAIL_ACKNOWLEDGEMENT -> hasText(evidence.getEmailAcknowledgementRef())
                    || hasText(evidence.getEmailAcknowledgementMetadata())
                    ? EvidenceStrength.MEDIUM
                    : EvidenceStrength.WEAK;
        };
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
