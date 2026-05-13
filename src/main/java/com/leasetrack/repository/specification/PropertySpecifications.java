package com.leasetrack.repository.specification;

import com.leasetrack.domain.entity.Property;
import com.leasetrack.domain.enums.UserRole;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

public final class PropertySpecifications {

    private PropertySpecifications() {
    }

    public static Specification<Property> accessibleTo(UUID userId, UserRole role) {
        return (root, query, criteriaBuilder) -> switch (role) {
            case ADMIN -> criteriaBuilder.conjunction();
            case LANDLORD, PROPERTY_MANAGER -> criteriaBuilder.equal(root.get("ownerUserId"), userId);
            case TENANT -> criteriaBuilder.disjunction();
        };
    }

    public static Specification<Property> search(String query) {
        return (root, criteriaQuery, criteriaBuilder) -> {
            if (query == null || query.isBlank()) {
                return criteriaBuilder.conjunction();
            }
            String pattern = "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
            return criteriaBuilder.or(
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), pattern),
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("addressLine1")), pattern),
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("city")), pattern),
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("province")), pattern),
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("postalCode")), pattern));
        };
    }
}
