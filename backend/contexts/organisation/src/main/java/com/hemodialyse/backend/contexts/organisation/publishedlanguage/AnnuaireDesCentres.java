package com.hemodialyse.backend.contexts.organisation.publishedlanguage;

import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Langage publié d'Organisation (organisation.v1, O4 lot A, ADR 0107) : ce que les autres contextes ont le droit de savoir des centres.
 * Lecture seule ; aucun état, aucune écriture.
 */
public interface AnnuaireDesCentres {

    /**
     * Les centres actifs, par identifiant croissant : seul un centre explicitement désactivé est exclu. Une seule définition pour tous les
     * consommateurs, Stock compris (KB-ORG-09 corrigé, ADR 0115) : l'état d'un centre n'est plus jamais NULL (migration V56, défaut TRUE).
     */
    List<UUID> actifs();

    /** Tous les centres, actifs ou non, dans un ordre non spécifié. */
    List<UUID> tous();

    /**
     * Le fuseau horaire du centre. Vide si le centre est inconnu ou si son fuseau est absent, vide ou illisible : chaque consommateur
     * applique alors SON fuseau par défaut (ce choix reste le sien).
     */
    Optional<ZoneId> fuseauHoraireDe(UUID centre);
}
