package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

/** Port sortant : les patients du centre (fiche tenue par Patient et couverture, hors contexte). */
public interface PatientsDuCentre {

    /** Vrai si le patient existe dans CE centre (inconnu ou d'un autre centre : faux). */
    boolean existe(CentreId centre, PatientId patient);
}
