package com.hemodialyse.backend.contexts.comptabilite.domain.periode;

import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;

import java.time.YearMonth;
import java.util.Optional;

/**
 * Port sortant : les périodes comptables d'un centre ; un mois sans ligne est ouvert.
 */
public interface PeriodeComptableRepository {

    Optional<PeriodeComptable> duMois(CentreId centre, YearMonth mois);

    /**
     * Crée la ligne du mois ou met à jour celle qui existe (jamais deux lignes pour un mois).
     */
    void enregistrer(PeriodeComptable periode);
}
