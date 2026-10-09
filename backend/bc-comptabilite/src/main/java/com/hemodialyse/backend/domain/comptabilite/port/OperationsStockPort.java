package com.hemodialyse.backend.domain.comptabilite.port;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Port sortant — opérations de stock à comptabiliser, déjà valorisées. La comptabilité ne dépend pas du module stock :
 * elle lit des montants et, pour chaque article, les comptes éventuellement précisés sur sa fiche (sinon vides : les
 * comptes du centre s'appliquent).
 */
public interface OperationsStockPort {

    /**
     * Centres dont le stock est à comptabiliser (traitement de nuit).
     */
    List<UUID> centres();

    /**
     * Bons de réception validés dont la date de réception est dans la période (bornes incluses).
     */
    List<Reception> receptions(UUID centerId, LocalDate du, LocalDate au);

    /**
     * Sorties de stock de la période, regroupées par jour, valorisées au prix moyen pondéré appliqué à la sortie.
     */
    List<SortiesDuJour> sorties(UUID centerId, LocalDate du, LocalDate au);

    /**
     * Inventaires clôturés dont la date est dans la période, avec leurs écarts valorisés.
     */
    List<Inventaire> inventaires(UUID centerId, LocalDate du, LocalDate au);

    /**
     * @param compteStockArticle  compte de stock précisé sur la fiche de l'article, ou {@code null}
     * @param compteChargeArticle compte de consommation précisé sur la fiche de l'article, ou {@code null}
     * @param montant             valeur hors taxe, positive
     */
    record Montant(String compteStockArticle, String compteChargeArticle, BigDecimal montant) {
    }

    record Reception(UUID bonId, String reference, LocalDate date, List<Montant> lignes) {
    }

    record SortiesDuJour(LocalDate jour, List<Montant> lignes) {
    }

    /**
     * @param compteStockArticle compte de stock de l'article, ou {@code null}
     * @param boni               valeur des excédents (compté &gt; théorique), positive ou nulle
     * @param mali               valeur des manquants (compté &lt; théorique), positive ou nulle
     */
    record Ecart(String compteStockArticle, BigDecimal boni, BigDecimal mali) {
    }

    record Inventaire(UUID inventaireId, String reference, LocalDate date, List<Ecart> ecarts) {
    }
}
