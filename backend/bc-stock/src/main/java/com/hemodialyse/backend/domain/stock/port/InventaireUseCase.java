package com.hemodialyse.backend.domain.stock.port;

import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.domain.stock.model.ComptageImporte;
import com.hemodialyse.backend.domain.stock.model.Inventaire;
import com.hemodialyse.backend.domain.stock.model.ResultatImportComptage;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Port In — inventaire de stock : ouverture (gel des mouvements), comptage, clôture ou annulation.
 */
public interface InventaireUseCase {

    Inventaire ouvrir(CenterId centerId, LocalDate dateInventaire, String commentaire, String user);

    Inventaire get(CenterId centerId, UUID inventaireId);

    PagedResult<InventaireRepositoryPort.InventaireResume> list(CenterId centerId, int page, int size);

    EtatInventaire etat(CenterId centerId);

    Inventaire compter(CenterId centerId, UUID inventaireId, UUID ligneId, BigDecimal quantite, String motif, String user);

    /**
     * Lot trouvé physiquement mais absent du stock théorique.
     */
    Inventaire ajouterLigne(CenterId centerId, UUID inventaireId, UUID articleId, String numeroLot, LocalDate datePeremption,
                            BigDecimal quantite, String motif, String user);

    Inventaire retirerLigne(CenterId centerId, UUID inventaireId, UUID ligneId);

    /**
     * Applique les quantités d'une feuille de comptage remplie : les lignes valides sont enregistrées, les lignes
     * introuvables, en double ou invalides sont signalées sans bloquer les autres.
     */
    ResultatImportComptage importerComptage(CenterId centerId, UUID inventaireId, List<ComptageImporte> lignes, String user);

    /**
     * Reporte la quantité théorique sur les lignes non comptées.
     */
    Inventaire reporterTheorique(CenterId centerId, UUID inventaireId, String user);

    /**
     * Stock de départ = quantités comptées ; mouvements antérieurs clôturés ; PMP / quantités recalculés.
     */
    Inventaire cloturer(CenterId centerId, UUID inventaireId, String user);

    Inventaire annuler(CenterId centerId, UUID inventaireId, String user);

    /**
     * Situation du centre : inventaire en cours (bloquant) et date du dernier inventaire clôturé.
     */
    record EtatInventaire(Inventaire enCours, LocalDate derniereCloture) {
    }
}



