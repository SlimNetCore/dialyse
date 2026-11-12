package com.hemodialyse.backend.contexts.patientcouverture.domain;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.Optional;

/** Le mois (AAAA-MM) qui filtre le tableau de bord ; absent ou vide : pas de filtre ; illisible : refusé (PL3, KB-PL3-4). */
public final class MoisDuTableauDeBord {

    private MoisDuTableauDeBord() {
    }

    public static Optional<YearMonth> lire(String brut) {
        if (brut == null || brut.isBlank()) return Optional.empty();
        try {
            return Optional.of(YearMonth.parse(brut));
        } catch (DateTimeParseException illisible) {
            throw new SaisieInvalide("month illisible : attendu AAAA-MM");
        }
    }
}
