package com.hemodialyse.backend.contexts.pilotagedirection.domain;

import java.time.LocalDate;
import java.time.Period;

/**
 * La tranche d'âge d'un patient, âge révolu À UNE DATE DE RÉFÉRENCE : le dernier jour de la période demandée (KB-DIR-08 corrigé le
 * 2026-11-10, lot L6 ; le legacy prenait le jour de l'appel). 18 ans révolus ce jour-là = {@code 18_29}, la veille = {@code 0_17} ; une
 * date de naissance absente ou postérieure à la date de référence = {@code INCONNU}.
 */
public enum TrancheDAge {
    DE_0_A_17("0_17"), DE_18_A_29("18_29"), DE_30_A_44("30_44"), DE_45_A_59("45_59"), SOIXANTE_ET_PLUS("60_PLUS"), INCONNUE("INCONNU");

    private final String code;

    TrancheDAge(String code) {
        this.code = code;
    }

    /** Le code servi dans le JSON, dans l'ordre d'affichage. */
    public String code() {
        return code;
    }

    public static TrancheDAge de(LocalDate naissance, LocalDate dateDeReference) {
        if (naissance == null || naissance.isAfter(dateDeReference)) return INCONNUE;
        int age = Period.between(naissance, dateDeReference).getYears();
        if (age < 18) return DE_0_A_17;
        if (age < 30) return DE_18_A_29;
        if (age < 45) return DE_30_A_44;
        if (age < 60) return DE_45_A_59;
        return SOIXANTE_ET_PLUS;
    }
}
