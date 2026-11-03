package com.hemodialyse.backend.contexts.patientcouverture.application;

import java.util.UUID;

/** Recherche paginée des patients du centre de la session (garde de centre sur toute lecture). */
public class RechercherLesPatients {

    private final SessionCourante session;
    private final LectureDesPatients lecture;

    public RechercherLesPatients(SessionCourante session, LectureDesPatients lecture) {
        this.session = session;
        this.lecture = lecture;
    }

    public PageDePatients executer(UUID centreDemande, CriteresDeRecherche criteres) {
        return lecture.rechercher(session.centre(centreDemande), criteres);
    }
}
