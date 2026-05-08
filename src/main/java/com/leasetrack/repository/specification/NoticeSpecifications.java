package com.leasetrack.repository.specification;

import com.leasetrack.domain.entity.DeliveryAttempt;
import com.leasetrack.domain.entity.Notice;
import com.leasetrack.domain.enums.DeliveryMethod;
import com.leasetrack.domain.enums.NoticeStatus;
import com.leasetrack.domain.enums.NoticeType;
import com.leasetrack.domain.enums.UserRole;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

public final class NoticeSpecifications {

    private NoticeSpecifications() {
    }

    public static Specification<Notice> hasStatus(NoticeStatus status) {
        return (root, query, criteriaBuilder) -> status == null
                ? criteriaBuilder.conjunction()
                : criteriaBuilder.equal(root.get("status"), status);
    }

    public static Specification<Notice> hasNoticeType(NoticeType noticeType) {
        return (root, query, criteriaBuilder) -> noticeType == null
                ? criteriaBuilder.conjunction()
                : criteriaBuilder.equal(root.get("noticeType"), noticeType);
    }

    public static Specification<Notice> hasDeliveryMethod(DeliveryMethod deliveryMethod) {
        return (root, query, criteriaBuilder) -> {
            if (deliveryMethod == null) {
                return criteriaBuilder.conjunction();
            }
            if (query != null) {
                query.distinct(true);
            }
            Join<Notice, DeliveryAttempt> attempts = root.join("deliveryAttempts", JoinType.INNER);
            return criteriaBuilder.equal(attempts.get("deliveryMethod"), deliveryMethod);
        };
    }

    public static Specification<Notice> hasDeadlineOnOrAfter(Instant deadlineAfter) {
        return (root, query, criteriaBuilder) -> {
            if (deadlineAfter == null) {
                return criteriaBuilder.conjunction();
            }
            if (query != null) {
                query.distinct(true);
            }
            Join<Notice, DeliveryAttempt> attempts = root.join("deliveryAttempts", JoinType.INNER);
            return criteriaBuilder.greaterThanOrEqualTo(attempts.get("deadlineAt"), deadlineAfter);
        };
    }

    public static Specification<Notice> hasDeadlineOnOrBefore(Instant deadlineBefore) {
        return (root, query, criteriaBuilder) -> {
            if (deadlineBefore == null) {
                return criteriaBuilder.conjunction();
            }
            if (query != null) {
                query.distinct(true);
            }
            Join<Notice, DeliveryAttempt> attempts = root.join("deliveryAttempts", JoinType.INNER);
            return criteriaBuilder.lessThanOrEqualTo(attempts.get("deadlineAt"), deadlineBefore);
        };
    }

    public static Specification<Notice> accessibleTo(UUID userId, UserRole role) {
        return (root, query, criteriaBuilder) -> switch (role) {
            case ADMIN -> criteriaBuilder.conjunction();
            case TENANT -> criteriaBuilder.equal(root.get("tenantUserId"), userId);
            case LANDLORD, PROPERTY_MANAGER -> criteriaBuilder.equal(root.get("ownerUserId"), userId);
        };
    }
}
