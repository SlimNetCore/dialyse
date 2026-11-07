package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.domain.DonneurVivant;
import com.hemodialyse.backend.contexts.dossiermedical.domain.DonneursVivants;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;

import java.util.List;
import java.util.UUID;

/**
 * Liste les donneurs vivants du patient du centre de la session, du plus récent au plus ancien. REPRODUIT : un patient inconnu ou d'un
 * autre centre a une liste vide (200). Lecture MEDECIN et ADMIN, non tracée en audit.
 */
public class ConsulterLesDonneursVivants {
    private final SessionCourante session;
    private final DonneursVivants donneurs;

    public ConsulterLesDonneursVivants(SessionCourante session, DonneursVivants donneurs) {
        this.session = session;
        this.donneurs = donneurs;
    }

    public List<DonneurVivant> executer(UUID centreDemande, UUID patientDemande) {
        return donneurs.duPatient(session.centre(centreDemande), new PatientId(patientDemande));
    }
}
