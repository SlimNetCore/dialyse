package com.hemodialyse.backend.contexts.seance.domain;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Tranche d'âge de la répartition du tableau de bord, calculée à la fin du mois lu : moins de 18 ans, 18 à 39, 40 à 59, 60 et plus,
 * ou inconnue sans date de naissance. Une naissance après la date de référence compte moins de 18 ans (reproduit).
 */
public enum TrancheDAge {
    DE_0_A_17("0-17"), DE_18_A_39("18-39"), DE_40_A_59("40-59"), DE_60_ET_PLUS("60+"), INCONNUE("INCONNU");

    private final String libelle;

    TrancheDAge(String libelle) {
        this.libelle = libelle;
    }

    /** Libellé exposé (clé de la répartition). */
    public String libelle() {
        return libelle;
    }

    public static TrancheDAge pour(LocalDate naissance, LocalDate reference) {
        if (naissance == null) return INCONNUE;
        long ans = ChronoUnit.YEARS.between(naissance, reference);
        if (ans < 18) return DE_0_A_17;
        if (ans < 40) return DE_18_A_39;
        if (ans < 60) return DE_40_A_59;
        return DE_60_ET_PLUS;
    }
}
