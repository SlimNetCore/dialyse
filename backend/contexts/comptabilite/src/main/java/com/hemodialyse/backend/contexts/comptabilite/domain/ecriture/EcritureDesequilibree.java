package com.hemodialyse.backend.contexts.comptabilite.domain.ecriture;

import com.hemodialyse.backend.contexts.comptabilite.domain.RegleMetierViolee;

import java.math.BigDecimal;

/**
 * La somme des débits diffère de celle des crédits au centime ({@code ECRITURE_DESEQUILIBREE}, 422).
 */
public class EcritureDesequilibree extends RegleMetierViolee {
    EcritureDesequilibree(BigDecimal debit, BigDecimal credit) {
        super("ECRITURE_DESEQUILIBREE", "L'écriture est déséquilibrée : débit=" + debit + " crédit=" + credit);
    }
}
