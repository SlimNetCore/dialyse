package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.domain.AbordVasculaire;
import com.hemodialyse.backend.contexts.dossiermedical.domain.AbordsVasculaires;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PageDeFiches;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;

import java.util.UUID;

/**
 * Consulte les abords vasculaires d'un patient du centre de la session, de la création la plus récente à la plus ancienne. Bornes
 * REPRODUITES du legacy (KB-MED-71) : page négative = 0, taille nulle ou négative = 20, plafond 200. Patient inconnu ou d'un autre
 * centre : page vide (200, reproduit). Lectures non tracées dans le journal d'audit (KB-MED-68, à confirmer avec le DPO). Droits :
 * MEDECIN, ADMIN et INFIRMIER (c'est lui qui ponctionne l'abord : KB-MED-69, reproduit).
 */
public class ConsulterLesAbordsVasculairesDuPatient {
    static final int TAILLE_PAR_DEFAUT = 20;
    static final int TAILLE_MAXIMALE = 200;

    private final SessionCourante session;
    private final AbordsVasculaires abords;

    public ConsulterLesAbordsVasculairesDuPatient(SessionCourante session, AbordsVasculaires abords) {
        this.session = session;
        this.abords = abords;
    }

    public PageDeFiches<AbordVasculaire> liste(UUID centreDemande, UUID patient, int page, int taille) {
        var centre = session.centre(centreDemande);
        int taillePosee = taille <= 0 ? TAILLE_PAR_DEFAUT : Math.min(taille, TAILLE_MAXIMALE);
        return abords.duPatient(centre, new PatientId(patient), Math.max(page, 0), taillePosee);
    }
}
