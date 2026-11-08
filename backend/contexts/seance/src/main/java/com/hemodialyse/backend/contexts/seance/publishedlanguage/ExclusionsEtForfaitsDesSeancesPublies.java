package com.hemodialyse.backend.contexts.seance.publishedlanguage;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Langage publié de la Séance ({@code seance.v1}, ADR 0097) : l'exclusion de la facturation, la réinclusion et le forfait imposé d'une
 * séance. Chaque écriture est LIMITÉE aux colonnes concernées et GARDÉE par l'état attendu de la séance (centre, statut, rattachement à une
 * facture) ; si aucune ligne n'est modifiée, elle lève {@link IllegalStateException} (le 422 de la Facturation) et ne change rien. Le port ne
 * décide pas des règles métier (le domaine de l'appelant les garde devant lui) ; il ne normalise rien : le statut est comparé en casse
 * exacte (KB-SEA-73) et la mémoire d'exclusion blanche est tenue pour présente par les écritures (KB-SEA-72, reproduits). N'ouvre aucune
 * transaction : il participe à celle de l'appelant (ADR 0097, décision 2).
 */
public interface ExclusionsEtForfaitsDesSeancesPublies {

    Optional<SeanceExcluablePublie> trouver(UUID centre, UUID seance);

    /**
     * Passe à {@code ABSENT} la séance qui a ce statut et n'est pas facturée ; mémorise ce statut.
     */
    void exclure(UUID centre, UUID seance, String statutAvant);

    /**
     * Mémorise l'absence de l'exclusion sur la séance {@code ABSENT} qui porte une mémoire d'exclusion et n'est pas facturée
     * (garde {@code facture_id IS NULL} ajoutée par KB-SEA-71, seul écart de la sous-slice).
     */
    void lierALAbsence(UUID centre, UUID seance, UUID absence);

    /**
     * Rend le statut d'avant l'exclusion à la séance {@code ABSENT} non facturée qui porte une mémoire d'exclusion, et efface la mémoire.
     */
    void reinclure(UUID centre, UUID seance, String statutRetabli);

    /**
     * Pose la surcharge de forfait (id, nom, prix, date, auteur) sur la séance {@code VALIDEE} ou {@code SIGNEE} non facturée. N'écrit PAS
     * {@code forfait_override_code} (KB-SEA-47, reproduit). Le prix est copié à l'identique.
     */
    void poserLeForfait(UUID centre, UUID seance, UUID forfait, String libelle, BigDecimal prixHt, String auteur, Instant le);

    /**
     * @param statutAvantExclusion mémoire de l'exclusion, {@code null} si absente OU blanche
     * @param facturee             vrai si la séance porte une {@code facture_id}
     */
    record SeanceExcluablePublie(UUID id, UUID patient, java.time.LocalDate date, String statut, boolean facturee,
                                 String statutAvantExclusion, UUID absenceDeLExclusion) {
    }
}
