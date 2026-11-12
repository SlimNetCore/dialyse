package com.hemodialyse.backend.contexts.patientcouverture.domain;

import java.time.LocalDate;

/**
 * Délai d'échéance du tableau de bord : nombre de jours, à partir du jour du centre, qui fixe le seuil des prises en charge et des
 * attestations « à échéance ». Trente jours par défaut, zéro admis, négatif refusé (PL3, KB-PL3-4).
 */
public record DelaiDEcheance(int jours) {

    public static final DelaiDEcheance PAR_DEFAUT = new DelaiDEcheance(30);

    public DelaiDEcheance {
        if (jours < 0) throw new SaisieInvalide("expirationDays ne peut pas être négatif");
    }

    /** Absent : le délai par défaut. */
    public static DelaiDEcheance de(Integer jours) {
        return jours == null ? PAR_DEFAUT : new DelaiDEcheance(jours);
    }

    /** Le seuil d'échéance pour un jour donné (le jour du centre). */
    public LocalDate seuilAu(LocalDate jour) {
        return jour.plusDays(jours);
    }
}
