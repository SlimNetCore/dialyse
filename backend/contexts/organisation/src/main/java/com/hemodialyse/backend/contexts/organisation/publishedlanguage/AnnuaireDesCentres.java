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
     * Les centres actifs, par identifiant croissant. Un centre dont l'état n'a jamais été renseigné est actif : seul un centre
     * explicitement désactivé est exclu (règle historique de la plateforme).
     */
    List<UUID> actifs();

    /**
     * Les centres actifs AU SENS STRICT, par identifiant croissant : seul un centre explicitement actif est retenu, un centre dont
     * l'état n'a jamais été renseigné est EXCLU (contrairement à {@link #actifs()}). Variante réservée au Stock, qui l'applique
     * aujourd'hui à son contrôle de nuit (O4-R3, ADR 0108, KB-ORG-09) ; à supprimer si les métiers confirment la variante large.
     */
    List<UUID> actifsAuSensStrict();

    /** Tous les centres, actifs ou non, dans un ordre non spécifié. */
    List<UUID> tous();

    /**
     * Le fuseau horaire du centre. Vide si le centre est inconnu ou si son fuseau est absent, vide ou illisible : chaque consommateur
     * applique alors SON fuseau par défaut (ce choix reste le sien).
     */
    Optional<ZoneId> fuseauHoraireDe(UUID centre);
}
