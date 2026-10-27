package com.hemodialyse.backend.contexts.planification.application;

import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.planification.domain.Presence.AlertePresence;
import com.hemodialyse.backend.contexts.planification.domain.Presence.AlerteSureffectif;
import com.hemodialyse.backend.contexts.planification.domain.Presence.DonneesDePresence;
import com.hemodialyse.backend.contexts.planification.domain.PresenceDeLaSemaine;
import com.hemodialyse.backend.contexts.planification.domain.ReglagesRepository;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.NotificationsDeLaPresence;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.PlanningDesPlaces;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Contrôle quotidien de la présence (RG-INF-046/047, job de 07:15) : pour chaque centre ACTIF (avec ou sans infirmier, KB-PLN-45), les
 * cases en sous-effectif des {@value #HORIZON_JOURS} prochains jours préviennent l'administration, le secrétariat et les médecins, les
 * cases en sur-effectif préviennent l'administrateur (cases, vacations, heures). « Aujourd'hui » est le jour du fuseau de chaque centre.
 * Chaque alerte est unique par centre, jour et type (KB-PLN-46). Les deux contrôles sont indépendants, et un centre en échec n'arrête
 * pas les autres : l'incident est rendu dans le résultat.
 */
public class ControlerLaPresence {
    public static final int HORIZON_JOURS = 14;

    private final CentresActifs centres;
    private final HorlogeDesCentres horloge;
    private final LecturesDeLaPresence lectures;
    private final ReglagesRepository reglages;
    private final NotificationsDeLaPresence notifications;

    public ControlerLaPresence(CentresActifs centres, HorlogeDesCentres horloge, PersonnelDeLaPresence personnel,
                               PlanningDesPlaces places, ReglagesRepository reglages, NotificationsDeLaPresence notifications) {
        this.centres = centres;
        this.horloge = horloge;
        this.lectures = new LecturesDeLaPresence(personnel, places);
        this.reglages = reglages;
        this.notifications = notifications;
    }

    /**
     * @param sousEffectif cases en sous-effectif signalées (0 : rien à signaler)
     * @param sureffectif  cases en sur-effectif signalées
     * @param incidents    causes d'arrêt de chacun des deux contrôles, vide si tout s'est bien passé
     */
    public record ResultatDuCentre(CentreId centre, LocalDate jour, int sousEffectif, boolean sousEffectifNotifie, int sureffectif,
                                   boolean sureffectifNotifie, List<String> incidents) {
    }

    /**
     * @param centres  un résultat par centre actif contrôlé
     * @param incident cause de l'échec de la liste des centres actifs (aucun centre n'a alors été contrôlé), nulle sinon
     */
    public record Bilan(List<ResultatDuCentre> centres, String incident) {
    }

    public Bilan controler() {
        List<CentreId> actifs;
        try {
            actifs = centres.liste();
        } catch (RuntimeException e) {
            return new Bilan(List.of(), description("liste des centres actifs", e));
        }
        List<ResultatDuCentre> resultats = new ArrayList<>();
        for (CentreId centre : actifs) {
            resultats.add(controler(centre));
        }
        return new Bilan(resultats, null);
    }

    ResultatDuCentre controler(CentreId centre) {
        LocalDate jour;
        try {
            jour = horloge.aujourdhui(centre);
        } catch (RuntimeException e) {
            return new ResultatDuCentre(centre, null, 0, false, 0, false, List.of(description("jour du centre", e)));
        }
        List<String> incidents = new ArrayList<>();
        DonneesDePresence donnees = null;
        LocalDate au = jour.plusDays(HORIZON_JOURS - 1L);
        try {
            donnees = lectures.charger(centre, PresenceDeLaSemaine.debutDeSemaine(jour),
                    PresenceDeLaSemaine.debutDeSemaine(au).plusDays(6));
        } catch (RuntimeException e) {
            incidents.add(description("lecture de la présence", e));
        }
        int manque = 0;
        boolean manqueNotifie = false;
        int surplus = 0;
        boolean surplusNotifie = false;
        if (donnees != null) {
            try {
                List<AlertePresence> alertes = PresenceDeLaSemaine.alertes(donnees, jour, au);
                manque = alertes.size();
                if (!alertes.isEmpty()) {
                    manqueNotifie = notifications.sousEffectif(centre.valeur(), jour, alertes.size(), alertes.get(0).date());
                }
            } catch (RuntimeException e) {
                incidents.add(description("sous-effectif", e));
            }
            try {
                List<AlerteSureffectif> alertes = PresenceDeLaSemaine.sureffectifs(donnees, jour, au);
                surplus = alertes.size();
                if (!alertes.isEmpty()) {
                    int vacations = alertes.stream().mapToInt(AlerteSureffectif::surplus).sum();
                    int heures = vacations * reglages.lire(centre).heuresParVacation();
                    surplusNotifie = notifications.sureffectif(centre.valeur(), jour, alertes.size(), vacations, heures,
                            alertes.get(0).date());
                }
            } catch (RuntimeException e) {
                incidents.add(description("sur-effectif", e));
            }
        }
        return new ResultatDuCentre(centre, jour, manque, manqueNotifie, surplus, surplusNotifie, List.copyOf(incidents));
    }

    private static String description(String controle, RuntimeException e) {
        return controle + " : " + e.getClass().getSimpleName() + (e.getMessage() == null ? "" : " : " + e.getMessage());
    }
}
