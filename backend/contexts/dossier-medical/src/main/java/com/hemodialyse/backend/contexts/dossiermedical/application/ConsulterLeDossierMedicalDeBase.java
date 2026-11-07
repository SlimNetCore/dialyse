package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.domain.DossierMedicalDeBase;
import com.hemodialyse.backend.contexts.dossiermedical.domain.DossiersMedicauxDeBase;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;

import java.util.Optional;
import java.util.UUID;

/**
 * Lit le dossier médical de base du patient du centre de la session. Ne crée rien. Dossier absent ou patient inconnu : vide (404 sans
 * corps, reproduit). Droits : MEDECIN, ADMIN et INFIRMIER (le statut d'hépatite conditionne l'isolement en machine). Seule lecture du
 * contexte journalisée en audit (KB-MED-100, reproduit, à confirmer avec le DPO).
 */
public class ConsulterLeDossierMedicalDeBase {
    private final SessionCourante session;
    private final DossiersMedicauxDeBase dossiers;

    public ConsulterLeDossierMedicalDeBase(SessionCourante session, DossiersMedicauxDeBase dossiers) {
        this.session = session;
        this.dossiers = dossiers;
    }

    public Optional<DossierMedicalDeBase> executer(UUID centreDemande, UUID patientDemande) {
        return dossiers.duPatient(session.centre(centreDemande), new PatientId(patientDemande));
    }
}
