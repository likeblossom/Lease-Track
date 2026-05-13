package com.leasetrack.repository.specification;

import com.leasetrack.domain.entity.Lease;
import com.leasetrack.domain.enums.UserRole;
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
}
