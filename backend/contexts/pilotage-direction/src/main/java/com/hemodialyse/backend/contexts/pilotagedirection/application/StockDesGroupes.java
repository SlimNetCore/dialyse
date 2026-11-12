package com.hemodialyse.backend.contexts.pilotagedirection.application;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Port sortant (PL10b2/PL10c, ADR 0152) : les groupes d'articles des centres (définis par centre) et la valorisation de leurs articles,
 * lus dans le langage publié du Stock (stock.v1). Lecture seule, sans SQL.
 */
public interface StockDesGroupes {

    /** Les groupes d'articles des centres demandés. */
    List<GroupeDArticles> groupes(Collection<UUID> centres);

    /** La valorisation de chaque article demandé sur [debut, finExclue[ (zéro pour un article sans mouvement). */
    Map<UUID, Valorisation> valoriser(UUID centre, Set<UUID> articles, OffsetDateTime debut, OffsetDateTime finExclue);

    /** {@code cle} : nom sans casse, accents ni espaces multiples (deux centres qui nomment un groupe pareil le consolident). */
    record GroupeDArticles(UUID id, UUID centre, String nom, String cle, Set<UUID> articles) {
    }

    /** Valeurs en DA : stock au début, entrées, sorties valorisées au PMP, autres variations (inventaires, ajustements, arrondis), stock à la fin. */
    record Valorisation(BigDecimal valeurDebut, BigDecimal entrees, BigDecimal sorties, BigDecimal autresVariations, BigDecimal valeurFin) {

        public static Valorisation zero() {
            BigDecimal z = BigDecimal.ZERO.setScale(2);
            return new Valorisation(z, z, z, z, z);
        }

        public Valorisation plus(Valorisation o) {
            return new Valorisation(valeurDebut.add(o.valeurDebut), entrees.add(o.entrees), sorties.add(o.sorties),
                    autresVariations.add(o.autresVariations), valeurFin.add(o.valeurFin));
        }
    }
}
