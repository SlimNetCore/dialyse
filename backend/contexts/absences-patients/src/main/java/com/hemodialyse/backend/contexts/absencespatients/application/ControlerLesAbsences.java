package com.hemodialyse.backend.contexts.absencespatients.application;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.absencespatients.domain.PeriodeDeDetection;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Contrôle des absences par le système (RG-ABS-040 à 045) : pour chaque centre actif, réconciliation puis détection des derniers
 * jours terminés. « Aujourd'hui » et la fenêtre se calculent au fuseau de chaque centre : un seul déclencheur serveur suffit. La
 * nuit (7 jours) le centre est ensuite prévenu s'il reste des absences à qualifier ; au démarrage (31 jours) on ne notifie pas.
 * Un centre en incident n'arrête pas les autres ; un absence en incident n'arrête pas son centre.
 */
public class ControlerLesAbsences {

    /** Fenêtre du contrôle de nuit : une semaine, pour absorber un arrêt de plusieurs jours du serveur. */
    public static final int JOURS_DE_LA_NUIT = 7;
    /** Fenêtre du rattrapage au démarrage : un mois (arrêt prolongé, déploiement récent). */
    public static final int JOURS_AU_DEMARRAGE = 31;

    private final CentresActifs centres;
    private final HorlogeDesCentres horloge;
    private final ReconciliationAbsences reconciliation;
    private final DetectionAbsences detection;
    private final NotificationDesAbsencesAQualifier notification;

    public ControlerLesAbsences(CentresActifs centres, HorlogeDesCentres horloge, ReconciliationAbsences reconciliation,
                                DetectionAbsences detection, NotificationDesAbsencesAQualifier notification) {
        this.centres = centres;
        this.horloge = horloge;
        this.reconciliation = reconciliation;
        this.detection = detection;
        this.notification = notification;
    }

    /**
     * Le résultat d'un centre : son bilan, la notification envoyée ou non, et l'incident qui a arrêté le centre (nul sinon).
     */
    public record ResultatDuCentre(CentreId centre, BilanDuControle bilan, boolean notifie, String incident) {
    }

    public List<ResultatDuCentre> controlerLaNuit() {
        return parcourir(JOURS_DE_LA_NUIT, true);
    }

    public List<ResultatDuCentre> rattraperAuDemarrage() {
        return parcourir(JOURS_AU_DEMARRAGE, false);
    }

    private List<ResultatDuCentre> parcourir(int jours, boolean notifier) {
        List<ResultatDuCentre> resultats = new ArrayList<>();
        for (CentreId centre : centres.liste()) {
            try {
                resultats.add(controler(centre, jours, notifier));
            } catch (RuntimeException e) {
                resultats.add(new ResultatDuCentre(centre, new BilanDuControle(0, 0, List.of(), List.of()), false,
                        ReconciliationAbsences.cause(e)));
            }
        }
        return resultats;
    }

    private ResultatDuCentre controler(CentreId centre, int jours, boolean notifier) {
        LocalDate aujourdhui = horloge.aujourdhui(centre);
        PeriodeDeDetection fenetre = PeriodeDeDetection.recente(aujourdhui, jours);
        Instant maintenant = horloge.maintenant();
        BilanDuControle bilan = reconciliation.executer(centre, fenetre, maintenant)
                .avec(detection.executer(centre, fenetre, maintenant));
        boolean notifie = notifier && notification.executer(centre, aujourdhui);
        return new ResultatDuCentre(centre, bilan, notifie, null);
    }
}
