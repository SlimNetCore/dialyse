package com.hemodialyse.backend.domain.comptabilite.port;

import java.time.YearMonth;
import java.util.UUID;

/**
 * Port sortant — gestion des périodes comptables (clôture/ouverture).
 */
public interface PeriodeComptableRepositoryPort {

    /**
     * Vérifie si la période donnée est clôturée pour ce centre.
     */
    boolean isClotured(UUID centerId, YearMonth periode);

    /**
     * Clôture la période. Opération irréversible.
     * Lance une BusinessException si la période est déjà clôturée.
     */
    void cloturer(UUID centerId, YearMonth periode, String userId);
}

