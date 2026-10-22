package com.hemodialyse.backend.contexts.absencespatients.domain;

import java.time.LocalDate;

/**
 * Délai de qualification : une absence encore « à qualifier » plus de {@value #JOURS} jours après la séance manquée est
 * <em>en retard</em> (RG-ABS-043). Constante du domaine, pas de paramétrage par centre. Règle de calcul pure : le jour
 * courant est fourni par l'appelant.
 */
public final class DelaiDeQualification {

    public static final int JOURS = 3;

    private DelaiDeQualification() {
    }

    /**
     * Date strictement postérieure à la séance manquée de toute absence en retard : une absence « à qualifier » dont la
     * séance précède cette limite est en retard.
     */
    public static LocalDate limite(LocalDate aujourdhui) {
        return aujourdhui.minusDays(JOURS);
    }

    public static boolean enRetard(StatutAbsence statut, LocalDate dateDeSeance, LocalDate aujourdhui) {
        return statut == StatutAbsence.A_QUALIFIER && dateDeSeance.isBefore(limite(aujourdhui));
    }
}
