package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.domain.PeriodeDeStatistiques;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.UUID;

/** Port sortant : la mise en page PDF du rapport de statistiques d'un patient (volet paramedical puis volet medical). */
public interface ImpressionDesStatistiquesDuPatient {

    byte[] enPdf(CentreId centre, UUID patient, PeriodeDeStatistiques periode, StatistiquesParamedicales paramedicales,
                 StatistiquesMedicales medicales);
}
