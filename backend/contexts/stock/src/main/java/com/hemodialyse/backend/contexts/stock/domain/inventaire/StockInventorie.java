package com.hemodialyse.backend.contexts.stock.domain.inventaire;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Port sortant : le stock vu par l'inventaire — le théorique à figer à l'ouverture, les lots et les mouvements du centre, et les
 * écritures de la clôture (stock des lots, lots trouvés, mouvements figés, stock de départ). Toutes les opérations s'exécutent dans
 * la transaction de l'appelant.
 */
public interface StockInventorie {

    /**
     * Le stock théorique : un lot par ligne (lots non épuisés des articles actifs), par libellé d'article puis FEFO (date de
     * péremption absente en dernier) ; un article actif dont le stock n'a aucun lot disponible donne une ligne sans lot.
     */
    List<LigneDInventaire> theorique(CentreId centre);

    /**
     * Un lot de l'article, retrouvé par son numéro (sans tenir compte de la casse), même épuisé.
     */
    Optional<LotConnu> lotDeLArticle(CentreId centre, UUID article, String numero);

    /**
     * Des mouvements du centre sont datés après {@code coupure}.
     */
    boolean mouvementsApres(CentreId centre, OffsetDateTime coupure);

    /**
     * Pose le stock du lot ; faux si le lot n'existe plus.
     */
    boolean poserLeStockDuLot(CentreId centre, UUID lot, BigDecimal quantite);

    /**
     * Crée le lot d'un lot trouvé en rayon (quantité initiale = quantité comptée).
     */
    UUID creerLeLot(CentreId centre, UUID article, String numero, LocalDate datePeremption, BigDecimal quantite, BigDecimal pmp,
                    OffsetDateTime creeLe);

    /**
     * Fige les mouvements des articles inventoriés datés jusqu'à {@code jusqua} : ils sont rattachés à l'inventaire et ignorés
     * du rejeu du PMP. Les autres articles ne sont pas touchés (KB-STK-50).
     */
    void figerLesMouvements(CentreId centre, UUID inventaire, OffsetDateTime jusqua, Set<UUID> articles);

    /**
     * Mouvement {@code INVENTAIRE} du lot : le stock de départ, valorisé au PMP de l'inventaire, daté de la coupure.
     */
    void poserLeStockDeDepart(CentreId centre, UUID article, UUID lot, BigDecimal quantite, BigDecimal pmp, String par,
                              OffsetDateTime coupure);

    record LotConnu(UUID id, LocalDate datePeremption) {
    }
}

