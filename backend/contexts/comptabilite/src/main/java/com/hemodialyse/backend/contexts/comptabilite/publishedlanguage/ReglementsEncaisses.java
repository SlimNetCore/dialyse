package com.hemodialyse.backend.contexts.comptabilite.publishedlanguage;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Langage publié (besoin de la Comptabilité, fourni par la Facturation) : les règlements encaissés d'un centre, en lecture
 * seule. Les règlements de reprise (saisis par la reprise des soldes d'ouverture, auteur {@code REPRISE}) des factures de reprise
 * en sont exclus (KB-FAC-67, décision du 2026-10-21) ; un encaissement saisi ensuite sur une telle facture y figure.
 */
public interface ReglementsEncaisses {

    /** Règlements dont la date est dans [du, au], par date puis identifiant. */
    List<ReglementEncaisse> encaissesEntre(UUID centre, LocalDate du, LocalDate au);

    /**
     * @param montant montant signé : négatif pour un remboursement
     * @param mode    mode de règlement ; nul pour les règlements d'avant la saisie du mode
     */
    record ReglementEncaisse(UUID id, UUID facture, BigDecimal montant, LocalDate date, String mode) {
    }
}
