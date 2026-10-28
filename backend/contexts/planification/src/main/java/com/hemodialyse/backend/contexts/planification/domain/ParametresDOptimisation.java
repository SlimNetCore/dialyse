package com.hemodialyse.backend.contexts.planification.domain;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Paramètres d'une optimisation du planning d'un centre (P21, RG-PLN-082), validés à la construction.
 *
 * @param perimetre              ce qui est planifié
 * @param debutSemaine           début de l'horizon (ramené au dimanche de sa semaine)
 * @param nbSemaines             semaines de l'horizon, de 1 à {@value #SEMAINES_MAX}. Couverture et maintenance les calculent une à
 *                               une (dates réelles) ; les autres périmètres calculent une semaine type que la proposition projette
 *                               sur chaque semaine de l'horizon, voir {@link #semainesCalcul()}
 * @param dureeMaxSecondes       temps de calcul maximal par phase
 * @param stabilite              de 0 (tout peut changer) à 10 (changer le moins possible les placements et le roulement)
 * @param objectif               arbitrage équité / économie de personnel
 * @param maxVacationsParJour    vacations (créneaux) maximales d'un infirmier dans la journée
 * @param maxVacationsParSemaine vacations hebdomadaires au-delà desquelles un infirmier est en dépassement
 * @param heuresParVacation      durée d'une vacation (heures), pour le quota d'heures des infirmiers
 * @param heuresHebdoTempsPlein  heures hebdomadaires d'un temps plein (quota d'un infirmier = au prorata de son taux)
 * @param reposHebdoMin          jours sans vacation exigés par semaine pour chaque infirmier
 */
public record ParametresDOptimisation(
        Perimetre perimetre,
        LocalDate debutSemaine,
        int nbSemaines,
        int dureeMaxSecondes,
        int stabilite,
        ObjectifDesInfirmiers objectif,
        int maxVacationsParJour,
        int maxVacationsParSemaine,
        int heuresParVacation,
        int heuresHebdoTempsPlein,
        int reposHebdoMin
) {

    /** Huit semaines : couvre une absence déclarée environ deux mois à l'avance. */
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

    public ParametresDOptimisation {
        if (perimetre == null) throw new SaisieInvalide("Périmètre requis");
        if (debutSemaine == null) throw new SaisieInvalide("Début de l'horizon requis");
        if (nbSemaines < 1 || nbSemaines > SEMAINES_MAX) {
            throw new SaisieInvalide("L'horizon doit être compris entre 1 et " + SEMAINES_MAX + " semaines");
        }
        if (dureeMaxSecondes < DUREE_MIN_SECONDES || dureeMaxSecondes > DUREE_MAX_SECONDES) {
            throw new SaisieInvalide("La durée de calcul doit être comprise entre " + DUREE_MIN_SECONDES + " et "
                    + DUREE_MAX_SECONDES + " secondes");
        }
        if (stabilite < 0 || stabilite > STABILITE_MAX) {
            throw new SaisieInvalide("La stabilité doit être comprise entre 0 et " + STABILITE_MAX);
        }
        if (maxVacationsParJour < 1 || maxVacationsParJour > VACATIONS_JOUR_MAX) {
            throw new SaisieInvalide("Les vacations par jour doivent être comprises entre 1 et " + VACATIONS_JOUR_MAX);
        }
        if (maxVacationsParSemaine < 1 || maxVacationsParSemaine > VACATIONS_SEMAINE_MAX) {
            throw new SaisieInvalide("Les vacations par semaine doivent être comprises entre 1 et " + VACATIONS_SEMAINE_MAX);
        }
        // mêmes bornes que les réglages du centre
        new ReglagesDePlanification(false, heuresParVacation, heuresHebdoTempsPlein, reposHebdoMin);
        objectif = objectif == null ? ObjectifDesInfirmiers.EQUITE : objectif;
        debutSemaine = PresenceDeLaSemaine.debutDeSemaine(debutSemaine);
    }

    /** Réglages de personnel par défaut. */
    public ParametresDOptimisation(Perimetre perimetre, LocalDate debutSemaine, int nbSemaines, int dureeMaxSecondes, int stabilite,
                                   ObjectifDesInfirmiers objectif, int maxVacationsParJour, int maxVacationsParSemaine) {
        this(perimetre, debutSemaine, nbSemaines, dureeMaxSecondes, stabilite, objectif, maxVacationsParJour,
                maxVacationsParSemaine, ReglagesDePlanification.HEURES_VACATION_PAR_DEFAUT,
                ReglagesDePlanification.HEURES_HEBDO_PAR_DEFAUT, ReglagesDePlanification.REPOS_PAR_DEFAUT);
    }

    public static ParametresDOptimisation parDefaut(Perimetre perimetre, LocalDate date) {
        return new ParametresDOptimisation(perimetre, date, 1, DUREE_PAR_DEFAUT, STABILITE_PAR_DEFAUT,
                ObjectifDesInfirmiers.EQUITE, VACATIONS_JOUR_PAR_DEFAUT, VACATIONS_SEMAINE_PAR_DEFAUT);
    }

    /** Mêmes paramètres avec les réglages de personnel du centre. */
    public ParametresDOptimisation avecReglages(ReglagesDePlanification reglages) {
        return new ParametresDOptimisation(perimetre, debutSemaine, nbSemaines, dureeMaxSecondes, stabilite, objectif,
                maxVacationsParJour, maxVacationsParSemaine, reglages.heuresParVacation(), reglages.heuresHebdoTempsPlein(),
                reglages.reposHebdoMin());
    }

    /**
     * Nombre de semaines nécessaires, à partir du début de la semaine de {@code depuis}, pour atteindre {@code jusquAu} (au moins
     * {@code minimum}, au plus {@link #SEMAINES_MAX}).
     */
    public static int semainesJusqua(LocalDate depuis, LocalDate jusquAu, int minimum) {
        LocalDate debut = PresenceDeLaSemaine.debutDeSemaine(depuis);
        long jours = ChronoUnit.DAYS.between(debut, jusquAu) + 1;
        long semaines = jours <= 0 ? 1 : (jours + 6) / 7;
        return (int) Math.max(1, Math.min(SEMAINES_MAX, Math.max(minimum, semaines)));
    }

    /** Semaines soumises au solveur : tout l'horizon pour un périmètre daté, la semaine type seule sinon. */
    public int semainesCalcul() {
        return perimetre.datee() ? nbSemaines : 1;
    }

    /** Dernier jour des semaines soumises au solveur (bornes incluses). */
    public LocalDate finCalcul() {
        return debutSemaine.plusDays(7L * semainesCalcul() - 1);
    }

    /** Dernier jour de l'horizon (bornes incluses). */
    public LocalDate finHorizon() {
        return debutSemaine.plusDays(7L * nbSemaines - 1);
    }
}
