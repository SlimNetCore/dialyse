package com.hemodialyse.backend.infrastructure.persistence.spec;

import com.hemodialyse.backend.infrastructure.persistence.entity.ObservationBiologiqueJpaEntity;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Critères de recherche des observations biologiques d'un patient : le couple
 * {@code patientId}/{@code centerId} est toujours appliqué (AGENTS.md §2), le code LOINC et la
 * période sont optionnels.
 */
public final class ObservationBiologiqueSpecifications {

    private ObservationBiologiqueSpecifications() {
    }

    public static Specification<ObservationBiologiqueJpaEntity> from(UUID patientId, UUID centerId,
                                                                     String loincCode, LocalDate from, LocalDate to) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("patientId"), patientId));
            predicates.add(cb.equal(root.get("centerId"), centerId));
            if (loincCode != null && !loincCode.isBlank()) {
                predicates.add(cb.equal(root.get("analyteCode"), loincCode));
            }
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
