package com.hemodialyse.backend.contexts.facturation.publishedlanguage;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Langage publié de la Facturation, côté FOURNI (ADR 0097) : l'exclusion de la facturation, la réinclusion et le forfait imposé d'une séance,
 * fournis par la plateforme à partir de {@code seance.v1}. Chaque écriture est gardée par l'état attendu de la séance ; sans effet sur une
 * ligne, elle lève {@link IllegalStateException} (le 422 de la Facturation). Ne normalise rien : le statut est comparé en casse exacte
 * (KB-SEA-73). Participe à la transaction de l'appelant.
 */
public interface ExclusionsDeSeancesFournies {

    Optional<SeanceExcluableFournie> trouver(UUID centre, UUID seance);

    void exclure(UUID centre, UUID seance, String statutAvant);

    void lierALAbsence(UUID centre, UUID seance, UUID absence);

    void reinclure(UUID centre, UUID seance, String statutRetabli);

    void poserLeForfait(UUID centre, UUID seance, UUID forfait, String libelle, BigDecimal prixHt, String auteur, Instant le);

    /**
     * @param statutAvantExclusion mémoire de l'exclusion, {@code null} si absente ou blanche
     */
    record SeanceExcluableFournie(UUID id, UUID patient, LocalDate date, String statut, boolean facturee, String statutAvantExclusion,
                                  UUID absenceDeLExclusion) {
    }
}
