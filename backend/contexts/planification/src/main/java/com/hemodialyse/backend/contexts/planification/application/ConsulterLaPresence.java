package com.hemodialyse.backend.contexts.planification.application;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.planification.domain.Presence.AlerteDoubleAffectation;
import com.hemodialyse.backend.contexts.planification.domain.Presence.AlertePresence;
import com.hemodialyse.backend.contexts.planification.domain.Presence.AlerteSureffectif;
import com.hemodialyse.backend.contexts.planification.domain.Presence.DonneesDePresence;
import com.hemodialyse.backend.contexts.planification.domain.Presence.SemainePresence;
import com.hemodialyse.backend.contexts.planification.domain.PresenceDeLaSemaine;
import com.hemodialyse.backend.contexts.planification.domain.SaisieInvalide;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.PlanningDesPlaces;

import java.time.LocalDate;
import java.util.List;

/**
 * Lit la présence des infirmiers (RG-INF-040 à 047) : la semaine, les alertes de sous-effectif, les alertes de sur-effectif et de double
 * affectation. « Aujourd'hui » est le jour du fuseau du centre, jamais celui d'UTC (KB-PLN-44). Lecture seule.
 */
public class ConsulterLaPresence {
    public static final int HORIZON_MAX_JOURS = 60;
    public static final int HORIZON_PAR_DEFAUT_JOURS = 14;

    private final LecturesDeLaPresence lectures;
    private final HorlogeDesCentres horloge;

    public ConsulterLaPresence(PersonnelDeLaPresence personnel, PlanningDesPlaces places, HorlogeDesCentres horloge) {
        this.lectures = new LecturesDeLaPresence(personnel, places);
        this.horloge = horloge;
    }

    /** Sur-effectifs (infirmiers au-delà de l'effectif requis) et infirmiers prévus dans deux salles au même créneau. */
    public record AlertesDeSureffectif(List<AlerteSureffectif> sureffectifs, List<AlerteDoubleAffectation> doublesAffectations) {
    }

    /**
     * @param date un jour de la semaine voulue, ou {@code null} pour la semaine courante du centre
     */
    public SemainePresence semaine(CentreId centre, LocalDate date) {
        LocalDate jour = date != null ? date : horloge.aujourdhui(centre);
        LocalDate debut = PresenceDeLaSemaine.debutDeSemaine(jour);
        return PresenceDeLaSemaine.construire(lectures.charger(centre, debut, debut.plusDays(6)), debut);
    }

    /** Cases en sous-effectif des {@code jours} prochains jours, aujourd'hui compris. */
    public List<AlertePresence> alertes(CentreId centre, int jours) {
        LocalDate aujourdhui = horloge.aujourdhui(centre);
        LocalDate au = fin(aujourdhui, jours);
        return PresenceDeLaSemaine.alertes(donnees(centre, aujourdhui, au), aujourdhui, au);
    }

    /** Sur-effectifs et doubles affectations des {@code jours} prochains jours, aujourd'hui compris. */
    public AlertesDeSureffectif sureffectifs(CentreId centre, int jours) {
        LocalDate aujourdhui = horloge.aujourdhui(centre);
        LocalDate au = fin(aujourdhui, jours);
        DonneesDePresence donnees = donnees(centre, aujourdhui, au);
        return new AlertesDeSureffectif(PresenceDeLaSemaine.sureffectifs(donnees, aujourdhui, au),
                PresenceDeLaSemaine.doublesAffectations(donnees, aujourdhui, au));
    }

    private static LocalDate fin(LocalDate aujourdhui, int jours) {
        if (jours < 1 || jours > HORIZON_MAX_JOURS) {
            throw new SaisieInvalide("L'horizon des alertes doit être compris entre 1 et " + HORIZON_MAX_JOURS + " jours");
        }
        return aujourdhui.plusDays(jours - 1L);
    }

    private DonneesDePresence donnees(CentreId centre, LocalDate aujourdhui, LocalDate au) {
        return lectures.charger(centre, PresenceDeLaSemaine.debutDeSemaine(aujourdhui),
                PresenceDeLaSemaine.debutDeSemaine(au).plusDays(6));
    }
}
