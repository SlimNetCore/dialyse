package com.hemodialyse.backend.contexts.comptabilite.domain.vente;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Règlement d'une facture tel que la Comptabilité le comptabilise (slice C5).
 *
 * @param facture la facture réglée ; nulle pour un règlement non rattaché
 * @param mode    {@code CAISSE} pour la caisse ; toute autre valeur vaut la banque
 * @param payeur  payeur du règlement, utilisé seulement si la facture n'a pas d'écriture de vente
 */
public record ReglementAComptabiliser(UUID id, UUID facture, BigDecimal montant, LocalDate date, String mode,
                                      String tiersLibelle, UUID payeur) {

    public ReglementAComptabiliser {
        if (id == null) throw new IllegalArgumentException("L'identifiant du règlement est obligatoire");
    }

    public boolean enCaisse() {
        return "CAISSE".equalsIgnoreCase(mode);
    }
}

