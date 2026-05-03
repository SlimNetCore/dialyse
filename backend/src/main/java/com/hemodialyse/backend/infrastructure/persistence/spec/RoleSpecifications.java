package com.hemodialyse.backend.infrastructure.persistence.spec;

import com.hemodialyse.backend.infrastructure.persistence.entity.AppRoleJpaEntity;
import com.hemodialyse.backend.infrastructure.web.dto.request.RoleSearchRequest;
import org.springframework.data.jpa.domain.Specification;

import jakarta.persistence.criteria.Predicate;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class RoleSpecifications {

    private RoleSpecifications() {
    }

    public static Specification<AppRoleJpaEntity> from(RoleSearchRequest req) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            addLike(predicates, cb, root.get("code"), req.code());
            addLike(predicates, cb, root.get("name"), req.name());
            addLike(predicates, cb, root.get("description"), req.description());

            if (req.search() != null && !req.search().isBlank()) {
                String term = likeTerm(req.search());
                predicates.add(cb.or(
                        cb.like(cb.lower(cb.coalesce(root.get("code"), "")), term),
                        cb.like(cb.lower(cb.coalesce(root.get("name"), "")), term),
                        cb.like(cb.lower(cb.coalesce(root.get("description"), "")), term)
                ));
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static void addLike(List<Predicate> predicates,
                                jakarta.persistence.criteria.CriteriaBuilder cb,
                                jakarta.persistence.criteria.Expression<String> field,
                                String value) {
        if (value == null || value.isBlank()) return;
        predicates.add(cb.like(cb.lower(cb.coalesce(field, "")), likeTerm(value)));
    }

    private static String likeTerm(String value) {
        return "%" + value.toLowerCase(Locale.ROOT).trim() + "%";
    }
}

