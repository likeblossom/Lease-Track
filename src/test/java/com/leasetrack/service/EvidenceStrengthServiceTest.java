package com.leasetrack.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.leasetrack.domain.entity.DeliveryAttempt;
import com.leasetrack.domain.entity.DeliveryEvidence;
import com.leasetrack.domain.enums.DeliveryMethod;
import com.leasetrack.domain.enums.EvidenceStrength;
import org.junit.jupiter.api.Test;

class EvidenceStrengthServiceTest {

    private final EvidenceStrengthService evidenceStrengthService = new EvidenceStrengthService();

    @Test
    void registeredMailWithTrackingAndDeliveryConfirmationIsStrong() {
        DeliveryAttempt attempt = attempt(DeliveryMethod.REGISTERED_MAIL);
        DeliveryEvidence evidence = new DeliveryEvidence();
        evidence.setTrackingNumber("RN123456789CA");
        evidence.setDeliveryConfirmation(true);

        EvidenceStrength strength = evidenceStrengthService.classify(attempt, evidence);

        assertThat(strength).isEqualTo(EvidenceStrength.STRONG);
    }

    @Test
    void emailAcknowledgementWithMetadataIsMedium() {
        DeliveryAttempt attempt = attempt(DeliveryMethod.EMAIL_ACKNOWLEDGEMENT);
        DeliveryEvidence evidence = new DeliveryEvidence();
        evidence.setEmailAcknowledgementMetadata("Recipient replied with acknowledgement");

        EvidenceStrength strength = evidenceStrengthService.classify(attempt, evidence);

        assertThat(strength).isEqualTo(EvidenceStrength.MEDIUM);
    }

    @Test
    void missingRequiredEvidenceIsWeak() {
        DeliveryAttempt attempt = attempt(DeliveryMethod.BAILIFF);
        DeliveryEvidence evidence = new DeliveryEvidence();

        EvidenceStrength strength = evidenceStrengthService.classify(attempt, evidence);

        assertThat(strength).isEqualTo(EvidenceStrength.WEAK);
    }

    private DeliveryAttempt attempt(DeliveryMethod deliveryMethod) {
        DeliveryAttempt attempt = new DeliveryAttempt();
        attempt.setDeliveryMethod(deliveryMethod);
        return attempt;
    }
}
