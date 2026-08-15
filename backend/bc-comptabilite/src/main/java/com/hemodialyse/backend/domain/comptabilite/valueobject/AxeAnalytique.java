package com.hemodialyse.backend.domain.comptabilite.valueobject;

/**
 * Axe analytique porté par une LigneEcriture.
 * Configurables (centre, forfait, type tiers) — jamais codés en dur.
 */
public record AxeAnalytique(String code, String libelle) {
    public AxeAnalytique {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Le code d'axe analytique est obligatoire");
        }
        if (libelle == null || libelle.isBlank()) {
            throw new IllegalArgumentException("Le libelle d'axe analytique est obligatoire");
        }
        code = code.trim().toUpperCase();
        libelle = libelle.trim();
    }

    public static AxeAnalytique centre(String centerId) {
        return new AxeAnalytique("CENTRE", "Centre " + centerId);
    }

    public static AxeAnalytique forfait(String codeForfait, String nomForfait) {
        return new AxeAnalytique("FORFAIT_" + codeForfait, nomForfait);
    }

    public static AxeAnalytique typeTiers(TypeTiersPayeur type) {
        return new AxeAnalytique("TIERS_" + type.name(), type.name());
    }
}

