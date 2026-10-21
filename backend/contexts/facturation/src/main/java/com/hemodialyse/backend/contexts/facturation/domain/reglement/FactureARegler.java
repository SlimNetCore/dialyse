package com.hemodialyse.backend.contexts.facturation.domain.reglement;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * La facture émise vue par les règlements : son total TTC figé et la somme signée déjà réglée (règlements moins
 * remboursements). Lue sous verrou au moment d'enregistrer un règlement.
 */
public record FactureARegler(UUID id, BigDecimal totalTtc, BigDecimal dejaRegle) {

    public FactureARegler {
        if (id == null) {
            throw new IllegalArgumentException("L'identifiant facture est obligatoire");
        }
        if (totalTtc == null || totalTtc.signum() < 0) {
            throw new IllegalArgumentException("Le montant facture doit etre positif");
        }
        totalTtc = totalTtc.setScale(2, RoundingMode.HALF_UP);
        dejaRegle = dejaRegle == null ? BigDecimal.ZERO.setScale(2) : dejaRegle.setScale(2, RoundingMode.HALF_UP);
    }
}
