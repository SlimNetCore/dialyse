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
}
