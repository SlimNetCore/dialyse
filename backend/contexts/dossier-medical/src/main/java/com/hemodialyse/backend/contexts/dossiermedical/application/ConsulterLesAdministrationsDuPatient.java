package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.domain.Administrations;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;

import java.util.UUID;

/**
 * Administrations d'un patient du centre de la session, de la plus récente à la plus ancienne. Bornes REPRODUITES du legacy : page
 * négative = 0, taille nulle ou négative = 20, plafond 200. Droits : MEDECIN, ADMIN et INFIRMIER.
 */
public class ConsulterLesAdministrationsDuPatient {

    private final SessionCourante session;
    private final Administrations administrations;

    public ConsulterLesAdministrationsDuPatient(SessionCourante session, Administrations administrations) {
        this.session = session;
        this.administrations = administrations;
    }

    public Administrations.PageDAdministrations executer(UUID centreDemande, UUID patient, int page, int taille) {
        var centre = session.centre(centreDemande);
        return administrations.duPatient(centre, new PatientId(patient), Math.max(page, 0), taille <= 0 ? 20 : Math.min(taille, 200));
    }
}
