package com.hemodialyse.backend.contexts.comptabilite.application.ecriture;

import com.hemodialyse.backend.contexts.comptabilite.domain.ecriture.EcritureComptable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Projection de consultation, stable pour le contrat HTTP historique.
 */
public record EcritureVue(UUID id, UUID centerId, String journalCode, LocalDate dateEcriture, LocalDate datePiece,
                          String numeroPiece, String libelle, String statut, BigDecimal totalDebit, boolean saisie,
                          List<LigneVue> lignes) {

    public EcritureVue {
        lignes = List.copyOf(lignes);
    }

    public static EcritureVue depuis(EcritureComptable ecriture) {
        return new EcritureVue(ecriture.id(), ecriture.centre().valeur(), ecriture.journal().valeur(),
                ecriture.dateEcriture(), ecriture.datePiece(), ecriture.numeroDePiece().valeur(), ecriture.libelle(),
                ecriture.statut().name(), ecriture.totalDebit(), ecriture.estSaisie(), ecriture.lignes().stream()
                .map(l -> new LigneVue(l.id(), l.compte(), l.libelle(), l.debit(), l.credit())).toList());
    }

    public record LigneVue(UUID id, String compteSCF, String libelleLigne, BigDecimal montantDebit,
                           BigDecimal montantCredit) {
    }
}
