package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.domain.EtapeDeBilan;
import com.hemodialyse.backend.contexts.dossiermedical.domain.EtapesDeBilan;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;

import java.util.List;
import java.util.UUID;

/**
 * Liste les étapes du bilan pré-greffe du patient du centre de la session, par catégorie puis libellé. REPRODUIT : un patient inconnu ou
 * d'un autre centre a une liste vide (200), comme les autres lectures du contexte. Lecture MEDECIN, ADMIN et INFIRMIER.
 */
public class ConsulterLesEtapesDeBilan {
    private final SessionCourante session;
    private final EtapesDeBilan etapes;

    public ConsulterLesEtapesDeBilan(SessionCourante session, EtapesDeBilan etapes) {
        this.session = session;
        this.etapes = etapes;
    }

    public List<EtapeDeBilan> executer(UUID centreDemande, UUID patientDemande) {
        return etapes.duPatient(session.centre(centreDemande), new PatientId(patientDemande));
    }
}
