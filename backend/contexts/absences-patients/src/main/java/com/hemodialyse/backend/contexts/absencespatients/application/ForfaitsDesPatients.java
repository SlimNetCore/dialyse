package com.hemodialyse.backend.contexts.absencespatients.application;

import com.hemodialyse.backend.contexts.absencespatients.domain.ForfaitApplicable;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.PatientId;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Port sortant : forfait de la prise en charge du patient applicable à une date (contexte Patient et couverture). Règle de choix
 * reproduite du legacy (KB-ABS-16) : la prise en charge dont la période effective ou demandée couvre la date, celle portant un
 * forfait effectif d'abord puis la plus récemment créée, le forfait effectif étant préféré au forfait demandé ; à défaut, la
 * plus récente portant un forfait, sans condition de date. Vide si le patient n'a aucun forfait.
 */
public interface ForfaitsDesPatients {

    Optional<ForfaitApplicable> applicable(CentreId centre, PatientId patient, LocalDate date);
}
