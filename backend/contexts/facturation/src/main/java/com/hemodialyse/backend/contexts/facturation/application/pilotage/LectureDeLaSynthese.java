package com.hemodialyse.backend.contexts.facturation.application.pilotage;

import com.hemodialyse.backend.contexts.facturation.publishedlanguage.SyntheseMensuelle;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.LocalDate;

/**
 * Port sortant de lecture : les données de la synthèse d'un centre sur une période (bornes incluses).
 */
public interface LectureDeLaSynthese {

    SyntheseMensuelle de(CentreId centre, LocalDate debut, LocalDate fin);
}
