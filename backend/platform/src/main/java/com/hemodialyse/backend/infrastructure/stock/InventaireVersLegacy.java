package com.hemodialyse.backend.infrastructure.stock;

import com.hemodialyse.backend.contexts.stock.domain.inventaire.Inventaire;
import com.hemodialyse.backend.contexts.stock.domain.inventaire.LigneDInventaire;
import com.hemodialyse.backend.domain.stock.model.InventaireStatut;
import com.hemodialyse.backend.domain.stock.model.LigneInventaire;

import java.util.List;

/**
 * Passerelle temporaire (ACL) vers le modèle d'inventaire historique, que la feuille Excel et le procès-verbal utilisent encore.
 * Retirée avec eux quand ils passeront au modèle du contexte Stock.
 */
final class InventaireVersLegacy {

    private InventaireVersLegacy() {
    }

    static com.hemodialyse.backend.domain.stock.model.Inventaire depuis(Inventaire inventaire) {
        List<LigneInventaire> lignes = inventaire.lignes().stream().map(InventaireVersLegacy::ligne).toList();
        return com.hemodialyse.backend.domain.stock.model.Inventaire.restore(inventaire.id(), inventaire.centre().valeur(),
                inventaire.reference(), inventaire.dateInventaire(), InventaireStatut.valueOf(inventaire.statut().name()),
                inventaire.commentaire(), inventaire.creePar(), inventaire.creeLe(), inventaire.clotureePar(), inventaire.clotureeLe(),
                lignes);
    }

    private static LigneInventaire ligne(LigneDInventaire l) {
        LigneInventaire ligne = LigneInventaire.theorique(l.articleId(), l.articleCode(), l.articleLibelle(), l.unite(), l.lotId(),
                l.numeroLot(), l.datePeremption(), l.quantiteTheorique(), l.pmp());
        ligne.setId(l.id());
        ligne.setQuantiteComptee(l.quantiteComptee());
        ligne.setMotifEcart(l.motifEcart());
        ligne.setComptePar(l.comptePar());
        ligne.setCompteLe(l.compteLe());
        ligne.setAjoutee(l.ajoutee());
        return ligne;
    }
}

