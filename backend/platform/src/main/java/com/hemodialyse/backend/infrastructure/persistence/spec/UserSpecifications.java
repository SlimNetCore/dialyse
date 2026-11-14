package com.hemodialyse.backend.infrastructure.persistence.spec;

import com.hemodialyse.backend.infrastructure.persistence.entity.AppRoleJpaEntity;
import com.hemodialyse.backend.infrastructure.persistence.entity.AppUserJpaEntity;
import com.hemodialyse.backend.contexts.organisation.publishedlanguage.AnnuaireDesCentres;
import com.hemodialyse.backend.infrastructure.web.dto.request.UserSearchRequest;
import org.springframework.data.jpa.domain.Specification;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

public final class UserSpecifications {

    private UserSpecifications() {
    }

    /**
     * @param annuaireDesCentres langage publié d'Organisation : le filtre « nom de centre » résout d'abord les identifiants des centres
     *                           dont le nom correspond (IDN-licence, ADR 0175 : plus d'entité de centre dans cette jointure)
     */
    public static Specification<AppUserJpaEntity> from(UserSearchRequest req, AnnuaireDesCentres annuaireDesCentres) {
        return (root, query, cb) -> {
            query.distinct(true);
            List<Predicate> predicates = new ArrayList<>();

            if (req.centerId() != null) {
                Join<AppUserJpaEntity, UUID> centerJoin = root.join("centerIds", JoinType.LEFT);
                predicates.add(cb.equal(centerJoin, req.centerId()));
            }

            addLike(predicates, cb, root.get("username"), req.username());
            addLike(predicates, cb, root.get("fullName"), req.fullName());
            addLike(predicates, cb, root.get("email"), req.email());

            if (req.roles() != null && !req.roles().isBlank()) {
                Join<AppUserJpaEntity, AppRoleJpaEntity> roleJoin = root.join("roles", JoinType.LEFT);
                predicates.add(cb.like(cb.lower(cb.coalesce(roleJoin.get("name"), "")), likeTerm(req.roles())));
            }
            if (req.centers() != null && !req.centers().isBlank()) {
                Pattern motif = motifLike(likeTerm(req.centers()));
                List<UUID> correspondants = annuaireDesCentres.tousAvecNom().stream()
                        .filter(c -> motif.matcher((c.nom() == null ? "" : c.nom()).toLowerCase(Locale.ROOT)).matches())
                        .map(AnnuaireDesCentres.CentreNomme::id).toList();
                if (correspondants.isEmpty()) {
                    predicates.add(cb.disjunction());
                } else {
                    Join<AppUserJpaEntity, UUID> centerJoin = root.join("centerIds", JoinType.LEFT);
                    predicates.add(centerJoin.in(correspondants));
                }
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

    /** La sémantique de {@code LIKE} (SQL) : {@code %} toute suite, {@code _} un caractère, {@code \} échappe le suivant. */
    private static Pattern motifLike(String like) {
        StringBuilder regex = new StringBuilder();
        for (int i = 0; i < like.length(); i++) {
            char c = like.charAt(i);
            if (c == '\\' && i + 1 < like.length()) {
                regex.append(Pattern.quote(String.valueOf(like.charAt(++i))));
            } else if (c == '%') {
                regex.append(".*");
            } else if (c == '_') {
                regex.append('.');
            } else {
                regex.append(Pattern.quote(String.valueOf(c)));
            }
        }
        return Pattern.compile(regex.toString(), Pattern.DOTALL);
    }

    private static String likeTerm(String value) {
        return "%" + value.toLowerCase(Locale.ROOT).trim() + "%";
    }
}

