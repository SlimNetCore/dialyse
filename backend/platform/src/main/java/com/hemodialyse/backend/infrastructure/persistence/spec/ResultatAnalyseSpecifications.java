package com.hemodialyse.backend.infrastructure.persistence.spec;

import com.hemodialyse.backend.infrastructure.persistence.entity.ResultatAnalyseJpaEntity;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Critères de recherche des bilans biologiques d'un patient.
 * <p>
 * Le couple {@code patientId}/{@code centerId} est toujours appliqué : le filtre par centre
 * n'est pas optionnel (AGENTS.md §2).
 */
public final class ResultatAnalyseSpecifications {

    private ResultatAnalyseSpecifications() {
    }

    public static Specification<ResultatAnalyseJpaEntity> from(UUID patientId,
                                                               UUID centerId,
                                                               LocalDate from,
                                                               LocalDate to) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("patientId"), patientId));
            predicates.add(cb.equal(root.get("centerId"), centerId));
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("datePrelevement"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("datePrelevement"), to));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
