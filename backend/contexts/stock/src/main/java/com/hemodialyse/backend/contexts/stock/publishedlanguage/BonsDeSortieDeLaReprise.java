package com.hemodialyse.backend.contexts.stock.publishedlanguage;

import java.util.List;
import java.util.UUID;

/**
 * Langage publié du Stock ({@code stock.v1}, RP3b, ADR 0172) : le contrôle des bons de sortie que l'annulation d'un lot de reprise de
 * données fait avant de supprimer un patient repris. Un bon de sortie rattaché à un patient (sortie de stock d'une séance) n'est jamais créé
 * par un lot : il a été saisi dans la plateforme et bloque la suppression du patient. Réservé à l'adapter d'annulation de la reprise (règle
 * ArchUnit) ; lecture seule, aucune transaction propre.
 */
public interface BonsDeSortieDeLaReprise {

    /**
     * Libellé observable de cette nature de donnée : celui que le refus d'annulation imprime (« patient « P1 » (bons_sortie) »), égal au nom
     * historique de la table ; ce n'est pas un nom de table offert aux appelants.
     */
    String NATURE = "bons_sortie";

    /**
     * Les identifiants (texte) des bons de sortie rattachés à ce patient, de N'IMPORTE QUEL centre (prudence voulue, comme pour le dossier
     * médical : KB-S6a-9, reproduit), sans ordre défini.
     */
    List<String> bonsDeSortieDuPatient(UUID patient);
}
