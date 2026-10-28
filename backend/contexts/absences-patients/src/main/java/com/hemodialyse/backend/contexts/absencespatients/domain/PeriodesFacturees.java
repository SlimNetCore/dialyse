package com.hemodialyse.backend.contexts.absencespatients.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.PatientId;

import java.time.LocalDate;

/**
 * Port sortant du domaine : ce que la Facturation sait de ses périodes (langage publié {@code FacturationDuPatient}).
 */
public interface PeriodesFacturees {

    /**
     * Vrai si une facture du patient couvre ce jour (bornes incluses, quel que soit son statut).
     */
    boolean estFacturee(CentreId centre, PatientId patient, LocalDate jour);
}
