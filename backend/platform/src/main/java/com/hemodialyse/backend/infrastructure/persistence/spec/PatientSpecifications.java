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
import java.util.Set;
import java.util.UUID;

public final class PatientSpecifications {

    private PatientSpecifications() {
    }

    public static Specification<PatientJpaEntity> from(UUID centerId, PatientSearchRequest req) {
        return from(centerId, req, ReferenceIdFilters.NONE);
    }

    public static Specification<PatientJpaEntity> from(UUID centerId, PatientSearchRequest req, ReferenceIdFilters refs) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("centerId"), centerId));

            addLike(predicates, root, cb, "codePatient", req.code());
            addLike(predicates, root, cb, "nom", req.nom());
            addLike(predicates, root, cb, "prenom", req.prenom());
            addLikeOrAnyOf(predicates, root, cb, "sexe", req.sexe());
            addLike(predicates, root, cb, "numeroAssurance", req.numeroAssurance());
            addLikeOrAnyOf(predicates, root, cb, "etatPatient", req.etatPatient());
            addIdIn(predicates, root, cb, "medecinTraitantId", refs.medecinTraitantIds());
            addIdIn(predicates, root, cb, "positionId", refs.positionIds());
            addIdIn(predicates, root, cb, "transporteurAllerId", refs.transporteurAllerIds());
            addIdIn(predicates, root, cb, "transporteurRetourId", refs.transporteurRetourIds());

            if (refs.pecForfaitIds() != null) {
                if (refs.pecForfaitIds().isEmpty()) {
                    predicates.add(cb.disjunction());
                } else {
                    // Forfait de la PEC validée (même source que la colonne « Forfait » de la liste).
                    Subquery<UUID> fsq = query.subquery(UUID.class);
                    Root<PecJpaEntity> fpec = fsq.from(PecJpaEntity.class);
                    fsq.select(fpec.get("id"));
                    fsq.where(
                            cb.equal(fpec.get("centerId"), root.get("centerId")),
                            cb.equal(fpec.get("patientId"), root.get("id")),
                            cb.equal(cb.lower(fpec.get("statut")), "validee"),
                            fpec.get("forfaitDemandeId").in(refs.pecForfaitIds())
                    );
                    predicates.add(cb.exists(fsq));
                }
            }

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

    /**
     * Restreint une colonne UUID de référentiel aux identifiants résolus ; ensemble vide → aucun résultat.
     */
    private static void addIdIn(List<Predicate> predicates, Root<PatientJpaEntity> root,
                                jakarta.persistence.criteria.CriteriaBuilder cb,
                                String field, Set<UUID> ids) {
        if (ids == null) return;
        predicates.add(ids.isEmpty() ? cb.disjunction() : root.get(field).in(ids));
    }

    /**
     * Filtre de liste déroulante à choix multiple : les valeurs séparées par des virgules (ex. {@code M,F}) sont
     * comparées exactement et le patient doit correspondre à l'une d'elles ; une saisie sans virgule reste une
     * recherche par contenu.
     */
    private static void addLikeOrAnyOf(List<Predicate> predicates, Root<PatientJpaEntity> root,
                                       jakarta.persistence.criteria.CriteriaBuilder cb,
                                       String field, String value) {
        if (value == null || value.isBlank()) return;
        if (!value.contains(",")) {
            addLike(predicates, root, cb, field, value);
            return;
        }
        List<String> choices = java.util.Arrays.stream(value.split(","))
                .map(v -> v.trim().toLowerCase(Locale.ROOT)).filter(v -> !v.isEmpty()).distinct().toList();
        if (choices.isEmpty()) return;
        predicates.add(cb.lower(root.get(field)).in(choices));
    }

    private static void addLike(List<Predicate> predicates, Root<PatientJpaEntity> root,
                                jakarta.persistence.criteria.CriteriaBuilder cb,
                                String field, String value) {
        if (value == null || value.isBlank()) return;
        predicates.add(cb.like(cb.lower(root.get(field)), likeTerm(value)));
    }

    /**
     * Filtres de colonne portant sur des référentiels (médecin, position, transporteurs, forfait) :
     * le texte saisi est résolu en amont en identifiants par libellé (voir PatientReferenceLookup).
     * {@code null} = colonne non filtrée ; ensemble vide = aucun référentiel ne correspond → aucun patient.
     */
    public record ReferenceIdFilters(Set<UUID> medecinTraitantIds,
                                     Set<UUID> positionIds,
                                     Set<UUID> transporteurAllerIds,
                                     Set<UUID> transporteurRetourIds,
                                     Set<UUID> pecForfaitIds) {
        public static final ReferenceIdFilters NONE = new ReferenceIdFilters(null, null, null, null, null);
    }

    private static String likeTerm(String value) {
        return "%" + value.toLowerCase(Locale.ROOT).trim() + "%";
    }
}

