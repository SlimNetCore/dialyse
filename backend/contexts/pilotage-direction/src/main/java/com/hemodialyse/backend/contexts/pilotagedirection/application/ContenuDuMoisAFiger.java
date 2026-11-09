package com.hemodialyse.backend.contexts.pilotagedirection.application;

import com.hemodialyse.backend.contexts.pilotagedirection.domain.ContenuFige;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Port sortant : constitue le contenu à figer d'une société sur un mois : sa vue d'ensemble, ses indicateurs et ses répartitions, calculés
 * maintenant et sérialisés (RG-DIR-090). Les indicateurs sont fournis par la plateforme (ADR 0102, 0103).
 */
public interface ContenuDuMoisAFiger {

    ContenuFige constituer(UUID societeId, LocalDate debut, LocalDate fin);
}
