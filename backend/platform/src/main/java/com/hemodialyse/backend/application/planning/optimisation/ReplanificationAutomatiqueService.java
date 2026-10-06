package com.hemodialyse.backend.application.planning.optimisation;

import com.hemodialyse.backend.application.notification.NotificationService;
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
 * deux semaines à venir, les déplacements temporaires liés aux maintenances des générateurs, puis le placement des
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

    public ReplanificationAutomatiqueService(OptimisationPlanningService planification,
                                             ReglagesOptimisationPort reglages, NotificationService notifications) {
        this.planification = planification;
        this.reglages = reglages;
        this.notifications = notifications;
    }

    public List<UUID> centres() {
        return reglages.centresEnReplanificationAuto();
    }

    /**
     * Lance l'enchaînement des calculs d'un centre ; chaque calcul démarre quand le précédent se termine.
     */
    public void replanifier(UUID centerId, LocalDate aujourdhui) {
        enchainer(centerId, etapes(aujourdhui), 0);
    }

    static List<ParametresOptimisation> etapes(LocalDate aujourdhui) {
        return List.of(
                parametres(PerimetreOptimisation.COUVERTURE, aujourdhui, SEMAINES),
                parametres(PerimetreOptimisation.MAINTENANCE, aujourdhui, SEMAINES),
                parametres(PerimetreOptimisation.PATIENTS, aujourdhui.plusWeeks(1), 1));
    }

    private static ParametresOptimisation parametres(PerimetreOptimisation perimetre, LocalDate debut, int semaines) {
        return new ParametresOptimisation(perimetre, debut, semaines, DUREE_SECONDES,
                ParametresOptimisation.STABILITE_PAR_DEFAUT, ObjectifInfirmiers.EQUITE,
                ParametresOptimisation.VACATIONS_JOUR_PAR_DEFAUT, ParametresOptimisation.VACATIONS_SEMAINE_PAR_DEFAUT);
    }

    private void enchainer(UUID centerId, List<ParametresOptimisation> etapes, int rang) {
        if (rang >= etapes.size()) return;
        try {
            planification.lancer(centerId, etapes.get(rang), UTILISATEUR, run -> {
                signaler(run);
                enchainer(centerId, etapes, rang + 1);
            });
        } catch (BusinessException e) {
            // un calcul lancé à la main est en cours : la nuit suivante reprendra
            log.info("[REPLANIFICATION] Centre {} : {} ({})", centerId, e.getMessage(), e.getCode());
        }
    }

    /**
     * Notifie une proposition terminée dont le motif a une valeur (sous-effectif à pourvoir, séances à déplacer, gain).
     */
    void signaler(RunOptimisation run) {
        if (run.statut() != StatutRun.TERMINEE || run.resultat() == null) return;
        PerimetreOptimisation perimetre = run.parametres().perimetre();
        MotifProposition.pour(perimetre).ifPresent(motif -> {
            int valeur = motif.valeur(run.resultat());
            if (valeur > 0) {
                notifications.notifyOptimisationProposition(run.centerId(), run.id(), perimetre.name(), motif.name(),
                        valeur);
            }
        });
    }
}
