package com.hemodialyse.backend.contexts.dossiermedical.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.List;

/** Port sortant du domaine, en lecture : les résultats d'analyses du patient (table {@code resultats_analyses}, tenue par la biologie, DM6). */
public interface BilansBiologiques {

    /** Les {@code nombre} bilans les plus récents (date de prélèvement décroissante). */
    List<BilanBiologique> lesPlusRecents(CentreId centre, PatientId patient, int nombre);
}
