package com.hemodialyse.backend.contexts.organisation.publishedlanguage;

import java.util.UUID;

/**
 * Langage publié d'Organisation (organisation.v1, O4 lot B, ADR 0108) : le verrou de la ligne d'un centre, qui sérialise les opérations
 * « centre entier » d'un autre contexte (par exemple la clôture d'une période de stock). Le verrou est pris dans la transaction en cours
 * de l'appelant et relâché avec elle ; sans transaction en cours, il est relâché aussitôt. Un centre inconnu n'est pas une erreur
 * (aucune ligne n'est verrouillée).
 */
public interface VerrouDuCentre {

    void verrouiller(UUID centre);

    /**
     * (O4 lot D, ADR 0110) Réserve la ligne du centre en écriture SANS bloquer les insertions des lignes qui référencent le centre :
     * mise à jour sans changement ({@code UPDATE centers SET id = id}). Sur PostgreSQL c'est un verrou {@code FOR NO KEY UPDATE}
     * (le verrou {@link #verrouiller} est {@code FOR UPDATE}, plus fort, et bloquerait ces insertions). Sert à sérialiser les écritures
     * d'un référentiel rattaché au centre (ADR 0056, infirmiers de la Planification). Même transaction que l'appelant ; un centre inconnu
     * n'est pas une erreur. Un verrou refusé (délai, interblocage) remonte en exception Spring que l'appelant traduit.
     */
    void reserverLeCentre(UUID centre);
}
