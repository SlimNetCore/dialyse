package com.hemodialyse.backend.domain.stock.port;

import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.domain.stock.model.BonSortie;
import com.hemodialyse.backend.domain.stock.model.SortieRequestItem;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Primary port: bon de sortie (BS) using FEFO, linked to a hemodialysis seance.
 */
public interface BonSortieUseCase {
    BonSortie create(CenterId centerId, UUID seanceId, UUID patientId, String poste,
                     LocalDate dateSortie, List<SortieRequestItem> items, String userId);

    BonSortie get(CenterId centerId, UUID bonId);

    List<BonSortie> list(CenterId centerId);

    BonSortie update(CenterId centerId, UUID bonId, UUID seanceId, UUID patientId,
                     String poste, LocalDate dateSortie, List<SortieRequestItem> items, String userId);

    /**
     * Ajoute {@code quantite} d'un article au bon de sortie « SEANCE » de la séance : le bon est créé à la première
     * ligne, puis mis à jour (une séance = un seul bon, jamais un bon par article).
     */
    BonSortie addSeanceConsommation(CenterId centerId, UUID seanceId, UUID patientId, LocalDate dateSeance,
                                    UUID articleId, BigDecimal quantite, String userId);

    /**
     * Fixe la quantité totale d'un article sur le bon de sortie « SEANCE » de la séance ({@code 0} retire la ligne) ;
     * lots, lignes et mouvements sont recalculés par FEFO sur le même bon.
     */
    BonSortie setSeanceConsommation(CenterId centerId, UUID seanceId, UUID patientId, LocalDate dateSeance,
                                    UUID articleId, BigDecimal quantite, String userId);

    /**
     * Sortie de stock FEFO à part entière (numéro de pièce dédié, distincte du bon « SEANCE » unique tenu par
     * {@link #addSeanceConsommation}) : sélectionne automatiquement les lots par FEFO puis crée
     * un {@link BonSortie} complet via {@link #create}. Utilisé par l'administration EPO/fer
     * pendant la séance, où chaque administration doit être traçable comme une sortie numérotée
     * distincte (pas une simple correction de mouvement).
     */
    BonSortie createViaFefo(CenterId centerId, UUID seanceId, UUID patientId, String poste,
                            LocalDate dateSortie, UUID articleId, BigDecimal quantite, String userId);
}

