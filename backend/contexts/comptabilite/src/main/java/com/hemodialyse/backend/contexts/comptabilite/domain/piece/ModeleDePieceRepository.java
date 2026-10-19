package com.hemodialyse.backend.contexts.comptabilite.domain.piece;

import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;

import java.util.Optional;
import java.util.UUID;

/**
 * Port sortant des modèles de pièce, toujours scoppé au centre.
 */
public interface ModeleDePieceRepository {

    PageModeles lister(CentreId centre, boolean actifsSeulement, int page, int taille);

    Optional<ModeleDePiece> parId(CentreId centre, UUID id);

    Optional<ModeleDePiece> parCode(CentreId centre, String code);

    void enregistrer(ModeleDePiece modele);

    void supprimer(CentreId centre, UUID id);

    record PageModeles(java.util.List<ModeleDePiece> items, long total, int page, int taille) {
    }
}
