package com.hemodialyse.backend.contexts.comptabilite.interfaces.http.dto;

import com.hemodialyse.backend.contexts.comptabilite.domain.piece.ModeleDePiece;

public record LigneModeleReponse(String sens, String compte, String libelle) {
    public static LigneModeleReponse depuis(ModeleDePiece.Ligne ligne) {
        return new LigneModeleReponse(ligne.sens().name(), ligne.compte(), ligne.libelle());
    }
}
