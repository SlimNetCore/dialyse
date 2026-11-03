package com.hemodialyse.backend.contexts.patientcouverture.application;

import com.hemodialyse.backend.contexts.patientcouverture.domain.PatientId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

/**
 * Résultat d'une admission ou d'une modification : ce que l'interface rend ({@code id}, {@code centerId}, {@code typePatient},
 * {@code codePatient}).
 *
 * @param refusDeLaPec code stable du refus de la demande de PEC de la fiche (ex. {@code PEC_IDENTIFIANT_DEJA_UTILISE}) : la
 *                     fiche est enregistrée mais la PEC ne l'est pas ; nul si tout est pris en compte
 */
public record PatientEnregistre(PatientId id, CentreId centre, boolean vacancier, String code, String refusDeLaPec) {

    public PatientEnregistre(PatientId id, CentreId centre, boolean vacancier, String code) {
        this(id, centre, vacancier, code, null);
    }
}
