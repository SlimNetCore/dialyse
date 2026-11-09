package com.hemodialyse.backend.contexts.stock.publishedlanguage;

import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port FOURNI AU Stock (O4 lot B, ADR 0108) : ce que le Stock a besoin de savoir des centres, dans ses propres termes. Le Stock ne peut pas
 * dépendre d'Organisation (Organisation dépend de Stock : cycle Maven) ; un traducteur de la plateforme implémente ce port en appelant le
 * langage publié d'Organisation. Lecture seule.
 */
public interface CentresFournis {

    /**
     * Les centres actifs, par identifiant croissant, au même sens que pour tous les contextes (KB-ORG-09 corrigé, ADR 0115) : seul un centre
     * désactivé en est exclu ; l'état d'un centre n'est plus jamais NULL.
     */
    List<UUID> centresActifs();

    /** Tous les centres, actifs ou non (comptabilisation de nuit), dans un ordre non spécifié. */
    List<UUID> tousLesCentres();

    /** Le fuseau horaire du centre ; vide si le centre est inconnu ou son fuseau absent, vide ou illisible (le Stock applique alors UTC). */
    Optional<ZoneId> fuseauHoraireDe(UUID centre);
}
