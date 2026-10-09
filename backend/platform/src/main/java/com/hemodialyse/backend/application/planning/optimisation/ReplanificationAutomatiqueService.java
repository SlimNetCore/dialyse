package com.hemodialyse.backend.application.planning.optimisation;

import com.hemodialyse.backend.application.notification.NotificationService;
import com.hemodialyse.backend.domain.infirmier.model.AbsenceInfirmier;
import com.hemodialyse.backend.domain.infirmier.port.PresenceDonneesPort;
import com.hemodialyse.backend.domain.planning.optimisation.model.MotifProposition;
import com.hemodialyse.backend.domain.planning.optimisation.model.ObjectifInfirmiers;
import com.hemodialyse.backend.domain.planning.optimisation.model.ParametresOptimisation;
import com.hemodialyse.backend.domain.planning.optimisation.model.PerimetreOptimisation;
import com.hemodialyse.backend.domain.planning.optimisation.model.RunOptimisation;
import com.hemodialyse.backend.domain.planning.optimisation.model.RunOptimisation.StatutRun;
import com.hemodialyse.backend.domain.planning.optimisation.port.ReglagesOptimisationPort;
import com.hemodialyse.backend.domain.shared.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Replanification automatique nocturne des centres qui l'ont activée : enchaîne, pour chaque centre, la couverture des
 * absences d'infirmiers à venir (deux semaines au moins, jusqu'à la fin de la dernière absence, huit au plus), les
 * déplacements temporaires liés aux maintenances des générateurs, puis le placement des
 * patients. Rien n'est appliqué : chaque proposition qui apporte quelque chose est signalée à l'administration, qui
 * la consulte et la valide (ou non) dans l'écran d'optimisation.
 */
@Service
public class ReplanificationAutomatiqueService {

    public static final String UTILISATEUR = "SYSTEME";
    static final int SEMAINES = 2;
    static final int DUREE_SECONDES = 30;
    private static final Logger log = LoggerFactory.getLogger(ReplanificationAutomatiqueService.class);

    private final OptimisationPlanningService planification;
    private final ReglagesOptimisationPort reglages;
    private final NotificationService notifications;
    private final PresenceDonneesPort presence;

    public ReplanificationAutomatiqueService(OptimisationPlanningService planification,
                                             ReglagesOptimisationPort reglages, NotificationService notifications,
                                             PresenceDonneesPort presence) {
        this.planification = planification;
        this.reglages = reglages;
        this.notifications = notifications;
        this.presence = presence;
    }

    public List<UUID> centres() {
        return reglages.centresEnReplanificationAuto();
    }

    static List<ParametresOptimisation> etapes(LocalDate aujourdhui) {
        return etapes(aujourdhui, SEMAINES);
    }

    static List<ParametresOptimisation> etapes(LocalDate aujourdhui, int semainesCouverture) {
        return List.of(
                parametres(PerimetreOptimisation.COUVERTURE, aujourdhui, semainesCouverture),
                parametres(PerimetreOptimisation.MAINTENANCE, aujourdhui, SEMAINES),
                // semaine type projetée sur deux semaines : l'administration parcourt la proposition semaine par semaine
                parametres(PerimetreOptimisation.PATIENTS, aujourdhui.plusWeeks(1), SEMAINES));
    }

    /**
     * Lance l'enchaînement des calculs d'un centre ; chaque calcul démarre quand le précédent se termine.
     */
    public void replanifier(UUID centerId, LocalDate aujourdhui) {
        enchainer(centerId, etapes(aujourdhui, semainesDeCouverture(centerId, aujourdhui)), 0, new Bilan());
    }

    /**
     * Les remplaçants se proposent jusqu'à la fin de la dernière absence d'infirmier à venir (au moins deux semaines,
     * au plus l'horizon maximal) : une absence planifiée dans plusieurs semaines est couverte dès maintenant, pas
     * seulement la semaine en cours.
     */
    int semainesDeCouverture(UUID centerId, LocalDate aujourdhui) {
        try {
            LocalDate limite = aujourdhui.plusWeeks(ParametresOptimisation.SEMAINES_MAX);
            LocalDate derniereFin = presence.charger(centerId, aujourdhui, limite).absences().stream()
                    .map(AbsenceInfirmier::fin)
                    .filter(fin -> !fin.isBefore(aujourdhui))
                    .max(LocalDate::compareTo)
                    .orElse(aujourdhui);
            return ParametresOptimisation.semainesJusqua(aujourdhui, derniereFin, SEMAINES);
        } catch (RuntimeException e) {
            log.warn("[REPLANIFICATION] Centre {} : absences à venir illisibles, horizon par défaut", centerId, e);
            return SEMAINES;
        }
    }

    private static ParametresOptimisation parametres(PerimetreOptimisation perimetre, LocalDate debut, int semaines) {
        return new ParametresOptimisation(perimetre, debut, semaines, DUREE_SECONDES,
                ParametresOptimisation.STABILITE_PAR_DEFAUT, ObjectifInfirmiers.EQUITE,
                ParametresOptimisation.VACATIONS_JOUR_PAR_DEFAUT, ParametresOptimisation.VACATIONS_SEMAINE_PAR_DEFAUT);
    }

    private void enchainer(UUID centerId, List<ParametresOptimisation> etapes, int rang, Bilan bilan) {
        if (rang >= etapes.size()) {
            notifierBilan(centerId, bilan.issue(), bilan);
            return;
        }
        try {
            planification.lancer(centerId, etapes.get(rang), UTILISATEUR, run -> {
                compter(run, bilan);
                enchainer(centerId, etapes, rang + 1, bilan);
            });
        } catch (BusinessException e) {
            // un calcul lancé à la main est en cours : la nuit suivante reprendra
            log.info("[REPLANIFICATION] Centre {} : {} ({})", centerId, e.getMessage(), e.getCode());
            notifierBilan(centerId, "REPORTEE", bilan);
        } catch (RuntimeException e) {
            // calcul impossible à lancer (données illisibles…) : compté en échec, les étapes suivantes sont tentées
            log.warn("[REPLANIFICATION] Centre {} : étape {} impossible à lancer", centerId,
                    etapes.get(rang).perimetre(), e);
            bilan.echecs++;
            enchainer(centerId, etapes, rang + 1, bilan);
        }
    }

    /**
     * Range l'exécution terminée dans le bilan de la nuit. Une notification impossible n'interrompt jamais
     * l'enchaînement des calculs.
     */
    private void compter(RunOptimisation run, Bilan bilan) {
        try {
            if (run.statut() == StatutRun.ECHEC) {
                bilan.echecs++;
            } else if (signaler(run)) {
                bilan.propositions++;
            }
        } catch (RuntimeException e) {
            log.warn("[REPLANIFICATION] Proposition {} non signalée", run.id(), e);
        }
    }

    private void notifierBilan(UUID centerId, String issue, Bilan bilan) {
        try {
            notifications.notifyReplanificationNocturne(centerId, issue, bilan.propositions, bilan.echecs);
        } catch (RuntimeException e) {
            log.warn("[REPLANIFICATION] Bilan de la nuit non signalé pour le centre {}", centerId, e);
        }
    }

    /**
     * Notifie une proposition terminée dont le motif a une valeur (sous-effectif à pourvoir, séances à déplacer, gain).
     *
     * @return vrai si la proposition mérite d'être examinée (elle a été notifiée)
     */
    boolean signaler(RunOptimisation run) {
        if (run.statut() != StatutRun.TERMINEE || run.resultat() == null) return false;
        PerimetreOptimisation perimetre = run.parametres().perimetre();
        MotifProposition motif = MotifProposition.pour(perimetre).orElse(null);
        if (motif == null) return false;
        int valeur = motif.valeur(run.resultat());
        if (valeur <= 0) return false;
        notifications.notifyOptimisationProposition(run.centerId(), run.id(), perimetre.name(), motif.name(), valeur);
        return true;
    }

    /**
     * Ce qu'une nuit a donné pour un centre : l'administrateur en reçoit toujours le bilan, même quand il n'y a rien à
     * examiner — sans quoi il ne peut pas savoir que la replanification a tourné.
     */
    static final class Bilan {
        private int propositions;
        private int echecs;

        /**
         * {@code PROPOSITIONS} : au moins une proposition à examiner ; {@code RIEN} : tout a été calculé, rien à
         * changer ; {@code ECHEC} : au moins un calcul n'a pas abouti.
         */
        String issue() {
            if (echecs > 0) return "ECHEC";
            return propositions > 0 ? "PROPOSITIONS" : "RIEN";
        }
    }
}
