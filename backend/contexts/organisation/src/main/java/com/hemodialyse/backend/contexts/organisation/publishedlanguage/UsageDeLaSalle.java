package com.hemodialyse.backend.contexts.organisation.publishedlanguage;

import java.util.UUID;

/**
 * Langage publié {@code organisation.v1} : ce qui retient une salle (PL11-4, D3, ADR 0160). Chaque propriétaire d'une référence à une
 * salle publie le nombre de ses références (Patient et couverture : patients et mouvements ; Séance : séances ; Planification :
 * affectations et remplacements d'infirmiers, cases du calendrier, déplacements temporaires ; GMAO : équipements) ; l'Organisation les
 * additionne pour décider entre supprimer et désactiver. Le compte est fait par identifiant de salle seul (KB-PL11-2, reproduit).
 * <p>
 * Une erreur technique est propagée telle quelle : l'Organisation refuse alors la suppression (D10).
 */
@FunctionalInterface
public interface UsageDeLaSalle {

    long nombreDeReferences(UUID salle);
}
