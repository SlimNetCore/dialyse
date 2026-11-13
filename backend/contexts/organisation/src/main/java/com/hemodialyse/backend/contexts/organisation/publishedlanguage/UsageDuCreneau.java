package com.hemodialyse.backend.contexts.organisation.publishedlanguage;

import java.util.UUID;

/**
 * Langage publié {@code organisation.v1} : ce qui retient un créneau (PL11-2, D3, ADR 0158). Chaque propriétaire d'une référence à un
 * créneau publie le nombre de ses références (Patient et couverture : patients et mouvements ; Séance : séances ; Planification :
 * affectations et remplacements d'infirmiers, cases du calendrier, déplacements temporaires) ; l'Organisation les additionne pour
 * décider entre supprimer et désactiver. Le compte est fait par identifiant de créneau seul (KB-PL11-2, reproduit).
 * <p>
 * Une erreur technique est propagée telle quelle : l'Organisation refuse alors la suppression (D10).
 */
@FunctionalInterface
public interface UsageDuCreneau {

    long nombreDeReferences(UUID creneau);
}
