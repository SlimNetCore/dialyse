package com.hemodialyse.backend.domain.planning.optimisation.model;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Période (bornes incluses) pendant laquelle un générateur en service ne peut pas servir : intervention GMAO planifiée
 * ou en cours.
 */
public record IndisponibiliteGenerateur(UUID generateurId, LocalDate debut, LocalDate fin, String motif) {

    public IndisponibiliteGenerateur {
        if (generateurId == null) throw new IllegalArgumentException("Générateur requis");
        if (debut == null || fin == null || fin.isBefore(debut)) throw new IllegalArgumentException("Période invalide");
    }

    public boolean couvre(LocalDate date) {
        return !date.isBefore(debut) && !date.isAfter(fin);
    }
}
