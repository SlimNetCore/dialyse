package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.domain.DemandesDExamen;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PageDeDemandesDExamen;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;

import java.util.UUID;

/**
 * Liste paginée des demandes d'examen d'un patient du centre de la session, de la plus récente à la plus ancienne. Bornes REPRODUITES
 * du legacy : page négative = 0, taille nulle ou négative = 20, plafond 200. Patient inconnu : page vide (200, reproduit). Les
 * lectures ne sont pas tracées dans le journal d'audit (KB-MED-43, à confirmer avec le DPO). Droits : MEDECIN et ADMIN.
 */
public class ConsulterLesDemandesDExamenDuPatient {

    static final int TAILLE_PAR_DEFAUT = 20;
    static final int TAILLE_MAXIMALE = 200;

    private final SessionCourante session;
    private final DemandesDExamen demandes;

    public ConsulterLesDemandesDExamenDuPatient(SessionCourante session, DemandesDExamen demandes) {
        this.session = session;
        this.demandes = demandes;
    }

    public PageDeDemandesDExamen executer(UUID centreDemande, UUID patient, int page, int taille) {
        var centre = session.centre(centreDemande);
        int taillePosee = taille <= 0 ? TAILLE_PAR_DEFAUT : Math.min(taille, TAILLE_MAXIMALE);
        return demandes.duPatient(centre, new PatientId(patient), Math.max(page, 0), taillePosee);
    }
}
