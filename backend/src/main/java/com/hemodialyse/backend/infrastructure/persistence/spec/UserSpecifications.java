package com.hemodialyse.backend.infrastructure.persistence.spec;

import com.hemodialyse.backend.infrastructure.persistence.entity.AppRoleJpaEntity;
import com.hemodialyse.backend.infrastructure.persistence.entity.AppUserJpaEntity;
import com.hemodialyse.backend.infrastructure.persistence.entity.CenterJpaEntity;
import com.hemodialyse.backend.infrastructure.web.dto.request.UserSearchRequest;
import org.springframework.data.jpa.domain.Specification;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class UserSpecifications {

    private UserSpecifications() {
    }

    public static Specification<AppUserJpaEntity> from(UserSearchRequest req) {
        return (root, query, cb) -> {
            query.distinct(true);
            List<Predicate> predicates = new ArrayList<>();

            if (req.centerId() != null) {
                Join<AppUserJpaEntity, CenterJpaEntity> centerJoin = root.join("centers", JoinType.LEFT);
                predicates.add(cb.equal(centerJoin.get("id"), req.centerId()));
            }

            addLike(predicates, cb, root.get("username"), req.username());
            addLike(predicates, cb, root.get("fullName"), req.fullName());
            addLike(predicates, cb, root.get("email"), req.email());

            if (req.roles() != null && !req.roles().isBlank()) {
                Join<AppUserJpaEntity, AppRoleJpaEntity> roleJoin = root.join("roles", JoinType.LEFT);
                predicates.add(cb.like(cb.lower(cb.coalesce(roleJoin.get("name"), "")), likeTerm(req.roles())));
            }
            if (req.centers() != null && !req.centers().isBlank()) {
                Join<AppUserJpaEntity, CenterJpaEntity> centerJoin = root.join("centers", JoinType.LEFT);
                predicates.add(cb.like(cb.lower(cb.coalesce(centerJoin.get("name"), "")), likeTerm(req.centers())));
            }
            if (req.active() != null) {
                predicates.add(cb.equal(root.get("active"), req.active()));
            }

            if (req.search() != null && !req.search().isBlank()) {
                String term = likeTerm(req.search());
                predicates.add(cb.or(
                        cb.like(cb.lower(cb.coalesce(root.get("username"), "")), term),
                        cb.like(cb.lower(cb.coalesce(root.get("fullName"), "")), term),
                        cb.like(cb.lower(cb.coalesce(root.get("email"), "")), term)
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

