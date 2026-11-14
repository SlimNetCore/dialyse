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

    /** Tous les centres, actifs ou non, avec leur nom, par nom croissant (le tri est celui d'Organisation, ADR 0174). */
    List<CentreNomme> tousAvecNom();

    /** Le nom d'un centre ; vide si le centre est inconnu (ADR 0174). */
    Optional<String> nomDuCentre(UUID centre);

    /**
     * Le code d'un centre (identifiant métier court, signé dans la clé de licence) ; vide si le centre est inconnu ou si son code est
     * absent (IDN-licence, ADR 0175).
     */
    Optional<String> codeDuCentre(UUID centre);

    /** Un centre et son nom. */
    record CentreNomme(UUID id, String nom) {
    }

    /**
     * Le fuseau horaire du centre. Vide si le centre est inconnu ou si son fuseau est absent, vide ou illisible : chaque consommateur
     * applique alors SON fuseau par défaut (ce choix reste le sien).
     */
    Optional<ZoneId> fuseauHoraireDe(UUID centre);
}
