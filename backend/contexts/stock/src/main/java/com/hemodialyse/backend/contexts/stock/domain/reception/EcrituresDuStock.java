package com.hemodialyse.backend.contexts.stock.domain.reception;

import com.hemodialyse.backend.contexts.stock.domain.CentreId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port sortant : écritures du Stock à la réception (lots, mouvements d'entrée, rejeu du PMP, verrou des articles) et gardes
 * d'inventaire (RG-STK-040, 041). Toutes les opérations s'exécutent dans la transaction de l'appelant.
 */
public interface EcrituresDuStock {

    /**
     * Verrouille les articles (verrou de ligne), dans l'ordre croissant des identifiants (aucun interblocage). Une écriture
     * concurrente attend ; au-delà du délai, {@link EcritureDeStockConcurrente}.
     */
    void verrouiller(CentreId centre, Collection<UUID> articles);

    /**
     * @throws com.hemodialyse.backend.contexts.stock.domain.referentiel.RefusDuReferentiel {@code STOCK_INVENTORY_IN_PROGRESS}
     *                                                                                      si un inventaire est en cours
     */
    void exigerAucunInventaireEnCours(CentreId centre);

    /**
     * @throws com.hemodialyse.backend.contexts.stock.domain.referentiel.RefusDuReferentiel {@code STOCK_PERIOD_CLOSED} si la
     *                                                                                      date est antérieure ou égale au dernier inventaire clôturé
     */
    void exigerUneDateOuverte(CentreId centre, LocalDate jour);

    List<Lot> lotsDuBon(CentreId centre, UUID bonId);

    void creerLot(Lot lot);

    void modifierLot(Lot lot);

    /**
     * Enregistre le mouvement d'entrée d'un lot, daté du jour à minuit UTC.
     */
    void enregistrerEntree(CentreId centre, UUID articleId, UUID lotId, BigDecimal quantite, BigDecimal prix, String auteur,
                           LocalDate jour, OffsetDateTime saisiLe);

    /**
     * Le mouvement d'entrée du lot, s'il existe.
     */
    Optional<EntreeDuLot> entreeDuLot(CentreId centre, UUID lotId);

    void dater(UUID mouvementId, LocalDate jour);

    void corrigerEntree(UUID mouvementId, BigDecimal quantite, BigDecimal prix, LocalDate jour);

    /**
     * Écrit le résultat d'un rejeu : {@code pmp_apres} de chaque mouvement, valorisation des sorties, PMP et quantité de
     * l'article.
     */
    void appliquerLeRejeu(CentreId centre, UUID articleId, List<MiseAJourDeMouvement> mouvements, BigDecimal pmp,
                          BigDecimal quantite);

    /**
     * @param valorisation prix de sortie à écrire, ou {@code null} pour le conserver
     */
    record MiseAJourDeMouvement(UUID mouvementId, BigDecimal pmpApres, BigDecimal valorisation) {
    }

    /**
     * @param cloture vrai si un inventaire a clôturé ce mouvement (il ne se modifie plus)
     */
    record EntreeDuLot(UUID mouvementId, boolean cloture) {
    }

    record Lot(UUID id, CentreId centre, UUID articleId, UUID bonReceptionId, UUID emplacementId, String numero,
               LocalDate datePeremption, BigDecimal quantiteInitiale, BigDecimal quantiteRestante, BigDecimal pmp) {
    }
}


