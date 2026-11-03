package com.hemodialyse.backend.contexts.patientcouverture.application;

import com.hemodialyse.backend.contexts.patientcouverture.domain.SaisieInvalide;

import java.util.UUID;

/**
 * Journal des mouvements du centre de la session. Page positive ou nulle, taille de 1 à 100 (décision du 02/11 : 400 au lieu du
 * recadrage silencieux, KB-PAT-13).
 */
public class ConsulterLesMouvements {

    public static final int TAILLE_MAXIMALE = 100;

    private final SessionCourante session;
    private final LectureDesMouvements lecture;

    public ConsulterLesMouvements(SessionCourante session, LectureDesMouvements lecture) {
        this.session = session;
        this.lecture = lecture;
    }

    public LectureDesMouvements.Page executer(UUID centreDemande, LectureDesMouvements.Filtre filtre, int page, int size) {
        var centre = session.centre(centreDemande);
        if (page < 0) throw new SaisieInvalide("La page ne peut pas être négative");
        if (size < 1 || size > TAILLE_MAXIMALE) throw new SaisieInvalide("La taille doit être comprise entre 1 et " + TAILLE_MAXIMALE);
        return lecture.lister(centre, filtre, page, size);
    }
}
