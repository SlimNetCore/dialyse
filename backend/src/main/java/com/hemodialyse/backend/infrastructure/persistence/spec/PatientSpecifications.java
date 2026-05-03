package com.hemodialyse.backend.infrastructure.persistence.spec;

import com.hemodialyse.backend.infrastructure.persistence.entity.PatientJpaEntity;
import com.hemodialyse.backend.infrastructure.persistence.entity.PecJpaEntity;
import com.hemodialyse.backend.infrastructure.web.dto.request.PatientSearchRequest;
import org.springframework.data.jpa.domain.Specification;

import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

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

            if (req.search() != null && !req.search().isBlank()) {
                String term = likeTerm(req.search());
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("codePatient")), term),
                        cb.like(cb.lower(root.get("nom")), term),
                        cb.like(cb.lower(root.get("prenom")), term),
                        cb.like(cb.lower(root.get("numeroAssurance")), term),
                        cb.like(cb.lower(root.get("etatPatient")), term)
                ));
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

    private static String likeTerm(String value) {
        return "%" + value.toLowerCase(Locale.ROOT).trim() + "%";
    }
}

