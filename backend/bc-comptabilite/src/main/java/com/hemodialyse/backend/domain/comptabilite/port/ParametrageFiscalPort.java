package com.hemodialyse.backend.domain.comptabilite.port;

import com.hemodialyse.backend.domain.comptabilite.valueobject.RegleTVA;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port sortant — lecture et gestion des règles TVA versionnées dans le temps.
 * La règle active à une date donnée est résolue ici — pas dans le domaine.
 */
public interface ParametrageFiscalPort {

    /**
     * Retourne la règle TVA active à la date donnée pour le type de prestation.
     * Retourne empty si aucune règle n'est configurée (traiter comme exonéré par défaut dans le générateur).
     */
    Optional<RegleTVA> findActiveAt(UUID centerId, String typePrestation, LocalDate date);

    List<RegleTVA> findAll(UUID centerId);

    void save(UUID centerId, RegleTVA regle);
}

