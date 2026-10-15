package com.hemodialyse.backend.domain.comptabilite.port;

import com.hemodialyse.backend.domain.comptabilite.valueobject.RegleTVA;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Port sortant — lecture de la règle TVA versionnée dans le temps. Les règles sont tenues par le contexte
 * {@code contexts/comptabilite} (slice C2) ; les Absences lisent ici la règle applicable par son langage publié (ACL
 * {@code infrastructure/acl/comptabilite}, retirée avec C7).
 */
public interface ParametrageFiscalPort {

    /**
     * Retourne la règle TVA active à la date donnée pour le type de prestation.
     * Retourne empty si aucune règle n'est configurée.
     */
    Optional<RegleTVA> findActiveAt(UUID centerId, String typePrestation, LocalDate date);
}
