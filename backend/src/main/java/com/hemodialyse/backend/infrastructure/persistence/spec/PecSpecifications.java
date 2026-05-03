package com.hemodialyse.backend.infrastructure.persistence.spec;

import com.hemodialyse.backend.infrastructure.persistence.entity.PatientJpaEntity;
import com.hemodialyse.backend.infrastructure.persistence.entity.PecJpaEntity;
import com.hemodialyse.backend.infrastructure.web.dto.request.PecSearchRequest;
import org.springframework.data.jpa.domain.Specification;

import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class PecSpecifications {

    private PecSpecifications() {
    }

    public static Specification<PecJpaEntity> from(PecSearchRequest req) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("centerId"), req.centerId()));

            if (req.code() != null && !req.code().isBlank()) {
                predicates.add(patientFieldLike(root, query, cb, "codePatient", req.code()));
            }
            if (req.assurance() != null && !req.assurance().isBlank()) {
                predicates.add(patientFieldLike(root, query, cb, "numeroAssurance", req.assurance()));
            }
            if (req.nom() != null && !req.nom().isBlank()) {
                predicates.add(patientNomPrenomLike(root, query, cb, req.nom()));
            }
            if (req.statut() != null && !req.statut().isBlank()) {
                predicates.add(cb.like(cb.lower(cb.coalesce(root.get("statut"), "")), likeTerm(req.statut())));
            }
            if (req.search() != null && !req.search().isBlank()) {
                String term = likeTerm(req.search());
                predicates.add(cb.or(
                        patientGlobalLike(root, query, cb, req.search()),
                        cb.like(cb.lower(cb.coalesce(root.get("statut"), "")), term)
                ));
            }

            DateRange debut = parseFlexibleDateRange(req.debut());
            if (debut != null) {
                if (debut.from != null)
                    predicates.add(cb.greaterThanOrEqualTo(root.get("dateDebutDemande"), debut.from));
                if (debut.to != null) predicates.add(cb.lessThanOrEqualTo(root.get("dateDebutDemande"), debut.to));
            }

            DateRange fin = parseFlexibleDateRange(req.fin());
            if (fin != null) {
                if (fin.from != null) predicates.add(cb.greaterThanOrEqualTo(root.get("dateFinDemande"), fin.from));
                if (fin.to != null) predicates.add(cb.lessThanOrEqualTo(root.get("dateFinDemande"), fin.to));
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static Predicate patientFieldLike(Root<PecJpaEntity> root,
                                              jakarta.persistence.criteria.CriteriaQuery<?> query,
                                              jakarta.persistence.criteria.CriteriaBuilder cb,
                                              String field,
                                              String value) {
        String term = likeTerm(value);
        Subquery<Long> sq = query.subquery(Long.class);
        Root<PatientJpaEntity> p = sq.from(PatientJpaEntity.class);
        sq.select(cb.literal(1L));
        sq.where(
                cb.equal(p.get("id"), root.get("patientId")),
                cb.equal(p.get("centerId"), root.get("centerId")),
                cb.like(cb.lower(cb.coalesce(p.get(field), "")), term)
        );
        return cb.exists(sq);
    }

    private static Predicate patientNomPrenomLike(Root<PecJpaEntity> root,
                                                  jakarta.persistence.criteria.CriteriaQuery<?> query,
                                                  jakarta.persistence.criteria.CriteriaBuilder cb,
                                                  String value) {
        String term = likeTerm(value);
        Subquery<Long> sq = query.subquery(Long.class);
        Root<PatientJpaEntity> p = sq.from(PatientJpaEntity.class);
        sq.select(cb.literal(1L));
        sq.where(
                cb.equal(p.get("id"), root.get("patientId")),
                cb.equal(p.get("centerId"), root.get("centerId")),
                cb.or(
                        cb.like(cb.lower(cb.coalesce(p.get("nom"), "")), term),
                        cb.like(cb.lower(cb.coalesce(p.get("prenom"), "")), term),
                        cb.like(
                                cb.lower(cb.concat(cb.concat(cb.coalesce(p.get("nom"), ""), " "), cb.coalesce(p.get("prenom"), ""))),
                                term
                        )
                )
        );
        return cb.exists(sq);
    }

    private static Predicate patientGlobalLike(Root<PecJpaEntity> root,
                                               jakarta.persistence.criteria.CriteriaQuery<?> query,
                                               jakarta.persistence.criteria.CriteriaBuilder cb,
                                               String value) {
        String term = likeTerm(value);
        Subquery<Long> sq = query.subquery(Long.class);
        Root<PatientJpaEntity> p = sq.from(PatientJpaEntity.class);
        sq.select(cb.literal(1L));
        sq.where(
                cb.equal(p.get("id"), root.get("patientId")),
                cb.equal(p.get("centerId"), root.get("centerId")),
                cb.or(
                        cb.like(cb.lower(cb.coalesce(p.get("codePatient"), "")), term),
                        cb.like(cb.lower(cb.coalesce(p.get("nom"), "")), term),
                        cb.like(cb.lower(cb.coalesce(p.get("prenom"), "")), term),
                        cb.like(cb.lower(cb.coalesce(p.get("numeroAssurance"), "")), term)
                )
        );
        return cb.exists(sq);
    }

    private static String likeTerm(String value) {
        return "%" + value.toLowerCase(Locale.ROOT).trim() + "%";
    }

    private static DateRange parseFlexibleDateRange(String value) {
        String raw = value == null ? "" : value.trim();
        if (raw.isBlank()) return null;

        if (!raw.contains("..")) {
            LocalDate exact = parseFlexibleDate(raw);
            return exact == null ? null : new DateRange(exact, exact);
        }

        String[] parts = raw.split("\\.\\.", 2);
        String fromRaw = parts.length > 0 ? parts[0].trim() : "";
        String toRaw = parts.length > 1 ? parts[1].trim() : "";

        LocalDate from = fromRaw.isBlank() ? null : parseFlexibleDate(fromRaw);
        LocalDate to = toRaw.isBlank() ? null : parseFlexibleDate(toRaw);

        if ((!fromRaw.isBlank() && from == null) || (!toRaw.isBlank() && to == null)) {
            return null;
        }
        if (from != null && to != null && from.isAfter(to)) {
            return new DateRange(to, from);
        }
        return new DateRange(from, to);
    }

    private static LocalDate parseFlexibleDate(String value) {
        String raw = value == null ? "" : value.trim();
        if (raw.isBlank()) return null;
        if (raw.contains("T")) raw = raw.substring(0, raw.indexOf('T'));
        if (raw.contains(" ")) raw = raw.substring(0, raw.indexOf(' '));
        try {
            return LocalDate.parse(raw);
        } catch (Exception ignored) {
        }
        try {
            return LocalDate.parse(raw, DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        } catch (Exception ignored) {
            return null;
        }
    }

    private record DateRange(LocalDate from, LocalDate to) {
    }
}

