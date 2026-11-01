package com.hemodialyse.backend.contexts.comptabilite.interfaces.http.dto;

import com.hemodialyse.backend.contexts.comptabilite.domain.piece.ModeleDePiece;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LigneModeleRequete(@NotBlank String sens, @NotBlank @Size(max = 20) String compte,
                                 @Size(max = ModeleDePiece.LIBELLE_MAX) String libelle) {
    public ModeleDePiece.Ligne vers() {
        ModeleDePiece.Sens valeur;
        try {
            valeur = ModeleDePiece.Sens.valueOf(sens);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Sens inconnu : " + sens + " (DEBIT ou CREDIT)");
        }
        return new ModeleDePiece.Ligne(valeur, compte, libelle);
    }
}
