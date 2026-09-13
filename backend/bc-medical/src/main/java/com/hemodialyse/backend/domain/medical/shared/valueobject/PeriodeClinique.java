package com.hemodialyse.backend.domain.medical.shared.valueobject;

import com.hemodialyse.backend.domain.shared.exception.BusinessException;

import java.time.LocalDate;

/**
 * Value Object — période clinique bornée dans le temps (début obligatoire, fin optionnelle
 * = « toujours en cours »). Centralise l'invariant « fin ≥ début » répété sur plusieurs
 * agrégats (antécédent, allergie, abord vasculaire).
 */
public record PeriodeClinique(LocalDate debut, LocalDate fin) {

    public PeriodeClinique {
        if (debut == null) {
            throw new BusinessException("PERIODE_DEBUT_REQUIS", "La date de début est obligatoire");
        }
        if (fin != null && fin.isBefore(debut)) {
            throw new BusinessException("PERIODE_FIN_ANTERIEURE", "La date de fin ne peut pas précéder la date de début");
        }
    }

    public static PeriodeClinique ouverte(LocalDate debut) {
        return new PeriodeClinique(debut, null);
    }

    public boolean estEnCours() {
        return fin == null;
    }
}
