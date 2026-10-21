package com.hemodialyse.backend.contexts.comptabilite.domain.vente;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Règlement d'une facture tel que la Comptabilité le comptabilise (slice C5).
 *
 * @param facture la facture réglée ; nulle pour un règlement non rattaché
 * @param montant montant signé : négatif pour un remboursement (écriture de sens inverse)
 * @param mode    mode de règlement : {@code CAISSE} et {@code ESPECES} entrent en caisse (journal de caisse), toute autre valeur
 *                ou l'absence de mode vaut la banque ({@code BANQUE}, {@code CHEQUE} : journal {@code BQ}) ; à valider par le métier
 * @param payeur  payeur du règlement, utilisé seulement si la facture n'a pas d'écriture de vente
 */
public record ReglementAComptabiliser(UUID id, UUID facture, BigDecimal montant, LocalDate date, String mode,
                                      String tiersLibelle, UUID payeur) {

    public ReglementAComptabiliser {
        if (id == null) throw new IllegalArgumentException("L'identifiant du règlement est obligatoire");
    }

    public boolean enCaisse() {
        return "CAISSE".equalsIgnoreCase(mode) || "ESPECES".equalsIgnoreCase(mode);
    }

    public boolean estUnRemboursement() {
        return montant != null && montant.signum() < 0;
    }
}

