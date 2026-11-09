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
     * Les centres actifs AU SENS STRICT du Stock, par identifiant croissant : un centre dont l'état n'a jamais été renseigné n'y figure pas
     * (comportement historique du contrôle de nuit des péremptions, O4-R3, KB-ORG-09).
     */
    List<UUID> centresActifsAuSensStrict();

    /** Tous les centres, actifs ou non (comptabilisation de nuit), dans un ordre non spécifié. */
    List<UUID> tousLesCentres();

    /** Le fuseau horaire du centre ; vide si le centre est inconnu ou son fuseau absent, vide ou illisible (le Stock applique alors UTC). */
    Optional<ZoneId> fuseauHoraireDe(UUID centre);
}
