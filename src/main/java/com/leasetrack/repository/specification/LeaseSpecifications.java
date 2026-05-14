package com.leasetrack.repository.specification;

import com.leasetrack.domain.entity.Lease;
import com.leasetrack.domain.enums.LeaseStatus;
import com.leasetrack.domain.enums.UserRole;
import jakarta.persistence.criteria.Predicate;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

public final class LeaseSpecifications {

    private LeaseSpecifications() {
    }

    public static Specification<Lease> accessibleTo(UUID userId, UserRole role) {
        return (root, query, criteriaBuilder) -> switch (role) {
            case ADMIN -> criteriaBuilder.conjunction();
            case LANDLORD, PROPERTY_MANAGER -> criteriaBuilder.equal(root.get("ownerUserId"), userId);
            case TENANT -> criteriaBuilder.disjunction();
        };
    }

    public static Specification<Lease> search(String query) {
        return (root, criteriaQuery, criteriaBuilder) -> {
            if (query == null || query.isBlank()) {
                return criteriaBuilder.conjunction();
            }
            String pattern = "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
            return criteriaBuilder.or(
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), pattern),
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("propertyAddress")), pattern),
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("tenantNames")), pattern));
        };
    }

    public static Specification<Lease> hasStatus(LeaseStatus status) {
        return (root, query, criteriaBuilder) -> {
            if (status == null || status == LeaseStatus.EXPIRING_SOON || status == LeaseStatus.EXPIRED) {
                return criteriaBuilder.conjunction();
            }
            return criteriaBuilder.equal(root.get("status"), status);
        };
    }

    public static Specification<Lease> belongsToUnit(UUID unitId) {
        return (root, query, criteriaBuilder) -> {
            if (unitId == null) {
                return criteriaBuilder.conjunction();
            }
            Predicate unitMatch = criteriaBuilder.equal(root.get("unit").get("id"), unitId);
            return unitMatch;
        };
    }
}
