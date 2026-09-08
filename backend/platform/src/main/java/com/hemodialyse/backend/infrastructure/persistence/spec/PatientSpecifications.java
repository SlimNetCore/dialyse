package com.hemodialyse.backend.infrastructure.persistence.spec;

import com.hemodialyse.backend.infrastructure.persistence.entity.PatientJpaEntity;
import com.hemodialyse.backend.infrastructure.persistence.entity.PecJpaEntity;
import com.hemodialyse.backend.infrastructure.web.dto.request.PatientSearchRequest;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class PatientSpecifications {

    private PatientSpecifications() {
    }

    public static Specification<PatientJpaEntity> from(UUID centerId, PatientSearchRequest req) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("centerId"), centerId));

            addLike(predicates, root, cb, "codePatient", req.code());
            addLike(predicates, root, cb, "nom", req.nom());
            addLike(predicates, root, cb, "prenom", req.prenom());
            addLike(predicates, root, cb, "sexe", req.sexe());
            addLike(predicates, root, cb, "numeroAssurance", req.numeroAssurance());
            addLike(predicates, root, cb, "etatPatient", req.etatPatient());
            addUuidLike(predicates, root, cb, "medecinTraitantId", req.medecinTraitantId());
            addUuidLike(predicates, root, cb, "positionId", req.positionId());
            addUuidLike(predicates, root, cb, "transporteurAllerId", req.transporteurAllerId());
            addUuidLike(predicates, root, cb, "transporteurRetourId", req.transporteurRetourId());

            LocalDate from = req.dateAdmissionFrom();
            LocalDate to = req.dateAdmissionTo();
            if (from != null && to != null && from.isAfter(to)) {
                LocalDate tmp = from;
                from = to;
                to = tmp;
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("dateAdmission"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("dateAdmission"), to));
            }


            if (req.nonFacturable() != null) {
                Subquery<UUID> sq = query.subquery(UUID.class);
                Root<PecJpaEntity> pec = sq.from(PecJpaEntity.class);
                sq.select(pec.get("id"));
                sq.where(
                        cb.equal(pec.get("centerId"), root.get("centerId")),
                        cb.equal(pec.get("patientId"), root.get("id")),
                        cb.equal(cb.lower(pec.get("statut")), "validee")
                );
                predicates.add(req.nonFacturable() ? cb.not(cb.exists(sq)) : cb.exists(sq));
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static void addLike(List<Predicate> predicates, Root<PatientJpaEntity> root,
                                jakarta.persistence.criteria.CriteriaBuilder cb,
                                String field, String value) {
        if (value == null || value.isBlank()) return;
        predicates.add(cb.like(cb.lower(root.get(field)), likeTerm(value)));
    }

    /**
     * Same as {@link #addLike} but for UUID columns, cast to text for the LIKE match.
     */
    private static void addUuidLike(List<Predicate> predicates, Root<PatientJpaEntity> root,
                                    jakarta.persistence.criteria.CriteriaBuilder cb,
                                    String field, String value) {
        if (value == null || value.isBlank()) return;
        predicates.add(cb.like(cb.lower(root.get(field).as(String.class)), likeTerm(value)));
    }

    private static String likeTerm(String value) {
        return "%" + value.toLowerCase(Locale.ROOT).trim() + "%";
    }
}

