package com.hemodialyse.backend.contexts.patientcouverture.application;

import java.util.UUID;

/** Recherche paginée des attestations du centre de la session (garde de centre sur toute lecture). */
public class RechercherLesAttestations {

    private final SessionCourante session;
    private final LectureDesAttestations lecture;

    public RechercherLesAttestations(SessionCourante session, LectureDesAttestations lecture) {
        this.session = session;
        this.lecture = lecture;
    }

    public LectureDesAttestations.Page executer(UUID centreDemande, CriteresDAttestation criteres) {
        return lecture.rechercher(session.centre(centreDemande), criteres);
    }
}
