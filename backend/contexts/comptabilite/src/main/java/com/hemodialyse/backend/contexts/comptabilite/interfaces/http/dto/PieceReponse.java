package com.hemodialyse.backend.contexts.comptabilite.interfaces.http.dto;

import com.hemodialyse.backend.contexts.comptabilite.domain.ecriture.EcritureComptable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record PieceReponse(UUID id, String numeroPiece, String journalCode, LocalDate dateEcriture,
                           String libelle, BigDecimal total) {
    public static PieceReponse depuis(EcritureComptable ecriture) {
        return new PieceReponse(ecriture.id(), ecriture.numeroDePiece().valeur(), ecriture.journal().valeur(),
                ecriture.dateEcriture(), ecriture.libelle(), ecriture.totalDebit());
    }
}
