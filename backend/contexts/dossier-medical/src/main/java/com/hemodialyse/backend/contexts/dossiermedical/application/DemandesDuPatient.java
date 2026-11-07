package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.UUID;

/** Port sortant : les demandes d'examen du patient (tenues par le même contexte, table {@code demandes_examen}). */
public interface DemandesDuPatient {

    /** Vrai si la demande existe pour CE patient dans CE centre (inconnue, d'un autre patient ou d'un autre centre : faux). */
    boolean existe(CentreId centre, PatientId patient, UUID demande);
}
