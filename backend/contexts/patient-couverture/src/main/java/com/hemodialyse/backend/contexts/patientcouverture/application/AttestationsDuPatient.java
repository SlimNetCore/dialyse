package com.hemodialyse.backend.contexts.patientcouverture.application;

import com.hemodialyse.backend.contexts.patientcouverture.domain.PatientId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.LocalDate;

/**
 * Port sortant de lecture des attestations de droits (la table appartient à PC4 : ACL de lecture sur {@code attestation_droit}
 * jusqu'à la reprise de cette slice).
 */
public interface AttestationsDuPatient {

    /** Le patient a-t-il une attestation dont la période couvre ce jour (bornes incluses) ? */
    boolean valideLe(CentreId centre, PatientId patient, LocalDate jour);
}
