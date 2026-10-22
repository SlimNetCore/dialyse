package com.hemodialyse.backend.contexts.absencespatients.domain;

import java.time.LocalDate;
import java.util.List;

/**
 * Règle de proposition des séances de rattrapage (RG-ABS-021) : service de domaine sans état. Seule une absence encore
 * modifiable peut être rattrapée ; une séance réalisée du patient est proposée si elle a lieu après la séance manquée,
 * au plus tard aujourd'hui, et si elle ne rattrape pas déjà une autre absence du patient.
 */
public final class SeancesDeRattrapage {

    private SeancesDeRattrapage() {
    }

    /**
     * @throws AbsenceNonModifiable l'absence est rattrapée ou annulée
     */
    public static void exigerRattrapable(AbsenceSuivie absence) {
        if (!absence.statut().modifiable()) {
            throw new AbsenceNonModifiable();
        }
    }

    /**
     * Premier jour où une séance peut rattraper l'absence : le lendemain de la séance manquée.
     */
    public static LocalDate premierJourPossible(AbsenceSuivie absence) {
        return absence.dateDeSeance().plusDays(1);
    }

    /**
     * @param realisees dates de séances réalisées du patient entre {@link #premierJourPossible} et aujourd'hui, de la
     *                  plus ancienne à la plus récente
     * @param utilisees dates déjà utilisées pour rattraper une autre absence du patient
     */
    public static List<LocalDate> proposees(List<LocalDate> realisees, List<LocalDate> utilisees) {
        return realisees.stream().filter(date -> !utilisees.contains(date)).toList();
    }
}
