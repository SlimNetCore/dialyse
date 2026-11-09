package com.hemodialyse.backend.contexts.pilotagedirection.application;

import com.hemodialyse.backend.contexts.pilotagedirection.domain.ContenuFige;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Port sortant : constitue le contenu à figer d'une société sur un mois : sa vue d'ensemble, ses indicateurs et ses répartitions, calculés
 * maintenant et sérialisés (RG-DIR-090). Les indicateurs sont composés par le Pilotage (ADR 0103, 0106).
 */
public interface ContenuDuMoisAFiger {

    ContenuFige constituer(UUID societeId, LocalDate debut, LocalDate fin);
}
