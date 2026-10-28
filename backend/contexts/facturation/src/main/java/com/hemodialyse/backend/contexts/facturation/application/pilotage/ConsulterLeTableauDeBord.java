package com.hemodialyse.backend.contexts.facturation.application.pilotage;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.YearMonth;

/**
 * Lecture du tableau de bord de facturation d'un centre. Seule règle : le mois est obligatoire.
 */
public final class ConsulterLeTableauDeBord {

    private final LectureDuTableauDeBord lecture;

    public ConsulterLeTableauDeBord(LectureDuTableauDeBord lecture) {
        this.lecture = lecture;
    }

    /**
     * @throws IllegalArgumentException si le mois est absent
     */
    public TableauDeBordDuMois executer(CentreId centre, YearMonth mois) {
        if (mois == null) {
            throw new IllegalArgumentException("Le mois est obligatoire");
        }
        return lecture.du(centre, mois);
    }
}
