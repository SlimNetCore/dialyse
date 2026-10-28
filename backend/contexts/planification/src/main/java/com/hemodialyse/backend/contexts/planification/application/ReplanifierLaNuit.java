package com.hemodialyse.backend.contexts.planification.application;

import com.hemodialyse.backend.contexts.planification.domain.EcritureConcurrente;
import com.hemodialyse.backend.contexts.planification.domain.ExecutionDOptimisation;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.planification.domain.MotifDeProposition;
import com.hemodialyse.backend.contexts.planification.domain.ObjectifDesInfirmiers;
import com.hemodialyse.backend.contexts.planification.domain.ParametresDOptimisation;
import com.hemodialyse.backend.contexts.planification.domain.Perimetre;
import com.hemodialyse.backend.contexts.planification.domain.Presence.AbsenceConnue;
import com.hemodialyse.backend.contexts.planification.domain.RegleDePlanificationViolee;
import com.hemodialyse.backend.contexts.planification.domain.ReglagesRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Replanification nocturne (P23, RG-PLN-100, job de 02:30 au fuseau par défaut) des centres ACTIFS qui l'ont activée (décision du
 * 28/10, KB-PLN-103) : « aujourd'hui » est le jour de chaque centre. Enchaîne, pour chaque centre, la couverture des absences d'infirmiers
 * à venir ({@value #SEMAINES} semaines au moins, jusqu'à la fin de la dernière absence, 8 au plus), les déplacements temporaires liés
 * aux maintenances, puis le placement des patients, {@value #DUREE_SECONDES} s au plus par phase. Rien n'est appliqué : chaque
 * proposition qui apporte quelque chose est signalée à l'administration. Le bilan est toujours envoyé ({@code PROPOSITIONS},
 * {@code RIEN}, {@code ECHEC} ou {@code REPORTEE}).
 */
public class ReplanifierLaNuit {

    public static final String UTILISATEUR = "SYSTEME";
    public static final int SEMAINES = 2;
    public static final int DUREE_SECONDES = 30;
    private static final Logger journal = Logger.getLogger(ReplanifierLaNuit.class.getName());

    private final ReglagesRepository reglages;
    private final HorlogeDesCentres horloge;
    private final LancerUneOptimisation lancer;
    private final PersonnelDeLaPresence personnel;
    private final NotificationsDeLOptimisation notifications;

    public ReplanifierLaNuit(ReglagesRepository reglages, HorlogeDesCentres horloge,
                             LancerUneOptimisation lancer, PersonnelDeLaPresence personnel, NotificationsDeLOptimisation notifications) {
        this.reglages = reglages;
        this.horloge = horloge;
        this.lancer = lancer;
        this.personnel = personnel;
        this.notifications = notifications;
    }

    /** Les centres à replanifier cette nuit : actifs, option activée, par identifiant. */
    public List<CentreId> centres() {
        return reglages.centresEnReplanificationNocturne();
    }

    /** Lance la nuit de tous les centres ; un centre en erreur n'empêche pas les autres. */
    public void executer() {
        for (CentreId centre : centres()) {
            try {
                replanifier(centre, horloge.aujourdhui(centre));
            } catch (RuntimeException e) {
                journal.log(Level.WARNING, "[REPLANIFICATION] Impossible pour le centre " + centre.valeur(), e);
            }
        }
    }

    public static List<ParametresDOptimisation> etapes(LocalDate aujourdhui, int semainesCouverture) {
        return List.of(
                parametres(Perimetre.COUVERTURE, aujourdhui, semainesCouverture),
                parametres(Perimetre.MAINTENANCE, aujourdhui, SEMAINES),
                // semaine type projetée sur deux semaines : l'administration parcourt la proposition semaine par semaine
                parametres(Perimetre.PATIENTS, aujourdhui.plusWeeks(1), SEMAINES));
    }

    /** Lance l'enchaînement des calculs d'un centre ; chaque calcul démarre quand le précédent se termine. */
    public void replanifier(CentreId centre, LocalDate aujourdhui) {
        enchainer(centre, etapes(aujourdhui, semainesDeCouverture(centre, aujourdhui)), 0, new Bilan());
    }

    /**
     * Les remplaçants se proposent jusqu'à la fin de la dernière absence d'infirmier à venir (au moins deux semaines, au plus
     * l'horizon maximal). Des absences illisibles laissent l'horizon par défaut.
     */
    public int semainesDeCouverture(CentreId centre, LocalDate aujourdhui) {
        try {
            LocalDate limite = aujourdhui.plusWeeks(ParametresDOptimisation.SEMAINES_MAX);
            LocalDate derniereFin = personnel.lire(centre, aujourdhui, limite).absences().stream()
                    .map(AbsenceConnue::fin)
                    .filter(fin -> !fin.isBefore(aujourdhui))
                    .max(LocalDate::compareTo)
                    .orElse(aujourdhui);
            return ParametresDOptimisation.semainesJusqua(aujourdhui, derniereFin, SEMAINES);
        } catch (RuntimeException e) {
            journal.log(Level.WARNING, "[REPLANIFICATION] Centre " + centre.valeur() + " : absences à venir illisibles, horizon par défaut", e);
            return SEMAINES;
        }
    }

    private static ParametresDOptimisation parametres(Perimetre perimetre, LocalDate debut, int semaines) {
        return new ParametresDOptimisation(perimetre, debut, semaines, DUREE_SECONDES, ParametresDOptimisation.STABILITE_PAR_DEFAUT,
                ObjectifDesInfirmiers.EQUITE, ParametresDOptimisation.VACATIONS_JOUR_PAR_DEFAUT,
                ParametresDOptimisation.VACATIONS_SEMAINE_PAR_DEFAUT);
    }

    private void enchainer(CentreId centre, List<ParametresDOptimisation> etapes, int rang, Bilan bilan) {
        if (rang >= etapes.size()) {
            notifierLeBilan(centre, bilan.issue(), bilan);
            return;
        }
        try {
            lancer.executer(centre, etapes.get(rang), UTILISATEUR, execution -> {
                compter(execution, bilan);
                enchainer(centre, etapes, rang + 1, bilan);
            });
        } catch (RegleDePlanificationViolee | EcritureConcurrente e) {
            // un calcul lancé à la main est en cours (ou le centre est occupé) : la nuit suivante reprendra
            journal.info("[REPLANIFICATION] Centre " + centre.valeur() + " : " + e.getMessage());
            notifierLeBilan(centre, "REPORTEE", bilan);
        } catch (RuntimeException e) {
            // calcul impossible à lancer (données illisibles...) : compté en échec, les étapes suivantes sont tentées
            journal.log(Level.WARNING, "[REPLANIFICATION] Centre " + centre.valeur() + " : étape " + etapes.get(rang).perimetre()
                    + " impossible à lancer", e);
            bilan.echecs++;
            enchainer(centre, etapes, rang + 1, bilan);
        }
    }

    /** Range l'exécution terminée dans le bilan de la nuit. Une notification impossible n'interrompt jamais l'enchaînement. */
    private void compter(ExecutionDOptimisation execution, Bilan bilan) {
        try {
            if (execution.statut() == ExecutionDOptimisation.Statut.ECHEC) {
                bilan.echecs++;
            } else if (signaler(execution)) {
                bilan.propositions++;
            }
        } catch (RuntimeException e) {
            journal.log(Level.WARNING, "[REPLANIFICATION] Proposition " + execution.id().valeur() + " non signalée", e);
        }
    }

    private void notifierLeBilan(CentreId centre, String issue, Bilan bilan) {
        try {
            notifications.bilanDeLaNuit(centre, issue, bilan.propositions, bilan.echecs);
        } catch (RuntimeException e) {
            journal.log(Level.WARNING, "[REPLANIFICATION] Bilan de la nuit non signalé pour le centre " + centre.valeur(), e);
        }
    }

    /** @return vrai si la proposition mérite d'être examinée (elle a été notifiée) */
    boolean signaler(ExecutionDOptimisation execution) {
        if (execution.statut() != ExecutionDOptimisation.Statut.TERMINEE || execution.resultat() == null) return false;
        Perimetre perimetre = execution.parametres().perimetre();
        MotifDeProposition motif = MotifDeProposition.pour(perimetre).orElse(null);
        if (motif == null) return false;
        int valeur = motif.valeur(execution.resultat());
        if (valeur <= 0) return false;
        notifications.propositionASignaler(execution.centre(), execution.id(), perimetre, motif.name(), valeur);
        return true;
    }

    /** Ce qu'une nuit a donné pour un centre : toujours notifié, même quand il n'y a rien à examiner. */
    private static final class Bilan {
        private int propositions;
        private int echecs;

        String issue() {
            if (echecs > 0) return "ECHEC";
            return propositions > 0 ? "PROPOSITIONS" : "RIEN";
        }
    }
}
