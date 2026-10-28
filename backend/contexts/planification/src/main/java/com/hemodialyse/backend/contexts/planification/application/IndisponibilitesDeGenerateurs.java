package com.hemodialyse.backend.contexts.planification.application;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.planification.domain.DonneesDOptimisation.IndisponibiliteDatee;

import java.time.LocalDate;
import java.util.List;

/**
 * Port sortant vers GMAO (langage publié {@code IndisponibilitesDesGenerateurs}) : les périodes pendant lesquelles un générateur de
 * dialyse du centre est immobilisé par une intervention planifiée ou en cours, qui chevauchent la période (bornes incluses).
 * Lecture seule, bornée au centre.
 */
public interface IndisponibilitesDeGenerateurs {
    List<IndisponibiliteDatee> entre(CentreId centre, LocalDate du, LocalDate au);
}
