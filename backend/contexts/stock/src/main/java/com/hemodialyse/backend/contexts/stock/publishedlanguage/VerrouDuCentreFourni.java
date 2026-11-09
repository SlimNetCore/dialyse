package com.hemodialyse.backend.contexts.stock.publishedlanguage;

import java.util.UUID;

/**
 * Port FOURNI AU Stock (O4 lot B, ADR 0108) : le verrou de la ligne d'un centre, pris dans la transaction en cours, qui sérialise les
 * opérations « centre entier » d'un inventaire (clôture de période). Un centre inconnu n'est pas une erreur. Implémenté par la plateforme
 * à partir du langage publié d'Organisation.
 */
public interface VerrouDuCentreFourni {

    void verrouiller(UUID centre);
}
