package com.hemodialyse.backend.contexts.comptabilite.interfaces.http.dto;

import com.hemodialyse.backend.contexts.comptabilite.domain.piece.ModeleDePiece;

import java.util.List;
import java.util.UUID;

public record ModeleReponse(UUID id, String code, String libelle, String journal, boolean actif,
                            List<LigneModeleReponse> lignes) {
    public static ModeleReponse depuis(ModeleDePiece modele) {
        return new ModeleReponse(modele.id(), modele.code(), modele.libelle(), modele.journal().valeur(),
                modele.actif(), modele.lignes().stream().map(LigneModeleReponse::depuis).toList());
    }
}
