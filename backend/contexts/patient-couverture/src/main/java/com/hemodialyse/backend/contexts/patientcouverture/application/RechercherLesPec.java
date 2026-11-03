package com.hemodialyse.backend.contexts.patientcouverture.application;

import java.util.UUID;

/** Recherche paginée des PEC du centre de la session (garde de centre sur toute lecture). */
public class RechercherLesPec {

    private final SessionCourante session;
    private final LectureDesPec lecture;

    public RechercherLesPec(SessionCourante session, LectureDesPec lecture) {
        this.session = session;
        this.lecture = lecture;
    }

    public LectureDesPec.Page executer(UUID centreDemande, CriteresDePec criteres) {
        return lecture.rechercher(session.centre(centreDemande), criteres);
    }
}
