package com.hemodialyse.backend.contexts.comptabilite.domain.stock;

import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Port sortant : opérations de stock à comptabiliser, déjà valorisées, lues chez le Stock (aucune écriture). La
 * Comptabilité ne dépend pas du module Stock : elle lit des montants et, pour chaque article, les comptes éventuellement
 * précisés sur sa fiche. Toute lecture est bornée au centre.
 */
public interface OperationsDeStock {

    /**
     * Centres dont le stock est à comptabiliser (tâche de nuit).
     */
    List<CentreId> centres();

    /**
     * Bons de réception validés dont la date de réception est dans [du, au].
     */
    List<Reception> receptions(CentreId centre, LocalDate du, LocalDate au);

    /**
     * Sorties de la période regroupées par jour UTC (B15), valorisées au prix appliqué, sinon au PMP.
     */
    List<SortiesDuJour> sorties(CentreId centre, LocalDate du, LocalDate au);

    /**
     * Inventaires clôturés dont la date est dans [du, au], avec leurs écarts valorisés.
     */
    List<Inventaire> inventaires(CentreId centre, LocalDate du, LocalDate au);

    /**
     * @param compteStockArticle  compte de stock précisé sur la fiche de l'article, ou {@code null}
     * @param compteChargeArticle compte de consommation précisé sur la fiche de l'article, ou {@code null}
     * @param montant             valeur hors taxe, positive ou nulle
     */
    record Montant(String compteStockArticle, String compteChargeArticle, BigDecimal montant) {
    }

    /**
     * @param articlesSansValeur articles d'une ligne sans prix (valeur nulle), à signaler (KB-CPT-42)
     */
    record Reception(UUID bonId, String reference, LocalDate date, List<Montant> lignes,
                     List<String> articlesSansValeur) {
    }

    /**
     * @param articlesSansValeur articles sortis sans prix ni PMP (valeur nulle), à signaler (KB-CPT-42)
     */
    record SortiesDuJour(LocalDate jour, List<Montant> lignes, List<String> articlesSansValeur) {
    }

    /**
     * @param boni valeur des excédents (compté &gt; théorique), positive ou nulle
     * @param mali valeur des manquants (compté &lt; théorique), positive ou nulle
     */
    record Ecart(String compteStockArticle, BigDecimal boni, BigDecimal mali) {
    }

    record Inventaire(UUID inventaireId, String reference, LocalDate date, List<Ecart> ecarts) {
    }
}

