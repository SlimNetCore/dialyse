package com.hemodialyse.backend.domain.planning.optimisation.model;

import com.hemodialyse.backend.domain.planning.service.PlanningSemaineService;

import java.time.LocalDate;

/**
 * Paramètres d'une optimisation du planning d'un centre.
 *
 * @param perimetre              ce qui est planifié
 * @param debutSemaine           début de l'horizon (ramené au dimanche de sa semaine)
 * @param nbSemaines             semaines planifiées (1, sauf couverture et maintenance : 1 à {@value #SEMAINES_MAX})
 * @param dureeMaxSecondes       temps de calcul maximal par phase
 * @param stabilite              de 0 (tout peut changer) à 10 (changer le moins possible les placements et le roulement)
 * @param objectif               arbitrage équité / économie de personnel
 * @param maxVacationsParJour    vacations (créneaux) maximales d'un infirmier dans la journée
 * @param maxVacationsParSemaine vacations hebdomadaires au-delà desquelles un infirmier est en dépassement
 * @param heuresParVacation      durée d'une vacation (heures), pour le quota d'heures des infirmiers
 * @param heuresHebdoTempsPlein  heures hebdomadaires d'un temps plein (quota d'un infirmier = au prorata de son taux)
 * @param reposHebdoMin          jours sans vacation exigés par semaine pour chaque infirmier
 */
public record ParametresOptimisation(
        PerimetreOptimisation perimetre,
        LocalDate debutSemaine,
        int nbSemaines,
        int dureeMaxSecondes,
        int stabilite,
        ObjectifInfirmiers objectif,
        int maxVacationsParJour,
        int maxVacationsParSemaine,
        int heuresParVacation,
        int heuresHebdoTempsPlein,
        int reposHebdoMin
) {

    /**
     * Huit semaines : couvre une absence déclarée environ deux mois à l'avance, comme l'horizon des alertes de présence
     * ({@code PresenceInfirmierQueryService.HORIZON_MAX_JOURS} = 60 jours).
     */
    public static final int SEMAINES_MAX = 8;
    public static final int DUREE_MIN_SECONDES = 2;
    public static final int DUREE_MAX_SECONDES = 300;
    public static final int DUREE_PAR_DEFAUT = 20;
    public static final int STABILITE_MAX = 10;
    public static final int STABILITE_PAR_DEFAUT = 5;
    public static final int VACATIONS_JOUR_MAX = 3;
    public static final int VACATIONS_JOUR_PAR_DEFAUT = 2;
    public static final int VACATIONS_SEMAINE_MAX = 14;
    public static final int VACATIONS_SEMAINE_PAR_DEFAUT = 6;

    public ParametresOptimisation {
        if (perimetre == null) throw new IllegalArgumentException("Périmètre requis");
        if (debutSemaine == null) throw new IllegalArgumentException("Début de l'horizon requis");
        if (nbSemaines < 1 || nbSemaines > SEMAINES_MAX) {
            throw new IllegalArgumentException("L'horizon doit être compris entre 1 et " + SEMAINES_MAX + " semaines");
        }
        if (!perimetre.datee() && nbSemaines != 1) {
            throw new IllegalArgumentException("Seules la couverture et la maintenance se planifient sur plusieurs semaines");
        }
        if (dureeMaxSecondes < DUREE_MIN_SECONDES || dureeMaxSecondes > DUREE_MAX_SECONDES) {
            throw new IllegalArgumentException("La durée de calcul doit être comprise entre " + DUREE_MIN_SECONDES
                    + " et " + DUREE_MAX_SECONDES + " secondes");
        }
        if (stabilite < 0 || stabilite > STABILITE_MAX) {
            throw new IllegalArgumentException("La stabilité doit être comprise entre 0 et " + STABILITE_MAX);
        }
        if (maxVacationsParJour < 1 || maxVacationsParJour > VACATIONS_JOUR_MAX) {
            throw new IllegalArgumentException("Les vacations par jour doivent être comprises entre 1 et " + VACATIONS_JOUR_MAX);
        }
        if (maxVacationsParSemaine < 1 || maxVacationsParSemaine > VACATIONS_SEMAINE_MAX) {
            throw new IllegalArgumentException(
                    "Les vacations par semaine doivent être comprises entre 1 et " + VACATIONS_SEMAINE_MAX);
        }
        // valide les réglages de personnel avec les mêmes bornes que le paramétrage du centre
        new ReglagesOptimisation(false, heuresParVacation, heuresHebdoTempsPlein, reposHebdoMin);
        objectif = objectif == null ? ObjectifInfirmiers.EQUITE : objectif;
        debutSemaine = PlanningSemaineService.debutSemaine(debutSemaine);
    }

    /**
     * Réglages de personnel par défaut ({@link ReglagesOptimisation#parDefaut()}).
     */
    public ParametresOptimisation(PerimetreOptimisation perimetre, LocalDate debutSemaine, int nbSemaines,
                                  int dureeMaxSecondes, int stabilite, ObjectifInfirmiers objectif,
                                  int maxVacationsParJour, int maxVacationsParSemaine) {
        this(perimetre, debutSemaine, nbSemaines, dureeMaxSecondes, stabilite, objectif, maxVacationsParJour,
                maxVacationsParSemaine, ReglagesOptimisation.HEURES_VACATION_PAR_DEFAUT,
                ReglagesOptimisation.HEURES_HEBDO_PAR_DEFAUT, ReglagesOptimisation.REPOS_PAR_DEFAUT);
    }

    /**
     * Paramètres par défaut pour un périmètre et une semaine.
     */
    public static ParametresOptimisation parDefaut(PerimetreOptimisation perimetre, LocalDate date) {
        return new ParametresOptimisation(perimetre, date, 1, DUREE_PAR_DEFAUT, STABILITE_PAR_DEFAUT,
                ObjectifInfirmiers.EQUITE, VACATIONS_JOUR_PAR_DEFAUT, VACATIONS_SEMAINE_PAR_DEFAUT);
    }

    /**
     * Mêmes paramètres avec les réglages de personnel du centre.
     */
    public ParametresOptimisation avecReglages(ReglagesOptimisation reglages) {
        return new ParametresOptimisation(perimetre, debutSemaine, nbSemaines, dureeMaxSecondes, stabilite, objectif,
                maxVacationsParJour, maxVacationsParSemaine, reglages.heuresParVacation(),
                reglages.heuresHebdoTempsPlein(), reglages.reposHebdoMin());
    }

    /**
     * Nombre de semaines nécessaires, à partir du début de la semaine de {@code depuis}, pour atteindre {@code jusquAu}
     * (au moins {@code minimum}, au plus {@link #SEMAINES_MAX}) : l'horizon d'une couverture doit aller jusqu'à la fin
     * de l'absence à pourvoir, pas seulement jusqu'à la semaine en cours.
     */
    public static int semainesJusqua(LocalDate depuis, LocalDate jusquAu, int minimum) {
        LocalDate debut = PlanningSemaineService.debutSemaine(depuis);
        long jours = java.time.temporal.ChronoUnit.DAYS.between(debut, jusquAu) + 1;
        long semaines = jours <= 0 ? 1 : (jours + 6) / 7;
        return (int) Math.max(1, Math.min(SEMAINES_MAX, Math.max(minimum, semaines)));
    }

    /**
     * Dernier jour de l'horizon (bornes incluses).
     */
    public LocalDate finHorizon() {
        return debutSemaine.plusDays(7L * nbSemaines - 1);
    }
}
