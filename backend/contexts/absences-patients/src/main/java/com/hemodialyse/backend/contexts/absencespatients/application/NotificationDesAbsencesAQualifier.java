package com.hemodialyse.backend.contexts.absencespatients.application;

import com.hemodialyse.backend.contexts.absencespatients.domain.DelaiDeQualification;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.absencespatients.domain.MoisDAbsencesOuverts;
import com.hemodialyse.backend.contexts.absencespatients.domain.SyntheseDesAbsences;
import com.hemodialyse.backend.contexts.absencespatients.publishedlanguage.NotificationsDuCentre;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Politique de notification (RG-ABS-044, 045) : après le contrôle de nuit, s'il reste des absences à qualifier, prévenir le centre
 * (nombre à qualifier, nombre en retard). Les comptes portent sur toutes les absences encore « à qualifier » des mois d'absences
 * ouverts, sans limite de fenêtre (KB-ABS-25 corrigé : les mois clos ne comptent plus). Une seule notification par centre et par jour
 * du centre (clé déterministe, sûre en multi-instance). Jamais au démarrage ni pour un rattrapage manuel.
 */
public class NotificationDesAbsencesAQualifier {

    private final LectureDesAbsences lecture;
    private final MoisDAbsencesOuverts mois;
    private final NotificationsDuCentre notifications;

    public NotificationDesAbsencesAQualifier(LectureDesAbsences lecture, MoisDAbsencesOuverts mois,
                                             NotificationsDuCentre notifications) {
        this.lecture = lecture;
        this.mois = mois;
        this.notifications = notifications;
    }

    /**
     * @param aujourdhui jour du centre
     * @return vrai si une notification a été envoyée
     */
    public boolean executer(CentreId centre, LocalDate aujourdhui) {
        SyntheseDesAbsences synthese = synthese(centre, aujourdhui);
        if (synthese.aQualifier() <= 0) {
            return false;
        }
        return notifications.signalerLesAbsencesAQualifier(centre.valeur(), aujourdhui, synthese.aQualifier(), synthese.enRetard());
    }

    SyntheseDesAbsences synthese(CentreId centre, LocalDate aujourdhui) {
        LocalDate limite = DelaiDeQualification.limite(aujourdhui);
        LocalDate dernierJourEnRetard = limite.minusDays(1);
        long aQualifier = lecture.nombreAQualifier(centre);
        long enRetard = lecture.nombreAQualifierAvant(centre, limite);
        for (YearMonth clos : mois.moisClos(centre)) {
            LocalDate du = clos.atDay(1);
            LocalDate au = clos.atEndOfMonth();
            aQualifier -= lecture.nombreAQualifierEntre(centre, du, au);
            LocalDate finDuRetard = au.isBefore(dernierJourEnRetard) ? au : dernierJourEnRetard;
            if (!du.isAfter(finDuRetard)) {
                enRetard -= lecture.nombreAQualifierEntre(centre, du, finDuRetard);
            }
        }
        return new SyntheseDesAbsences(aQualifier, enRetard);
    }
}
