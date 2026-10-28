package com.hemodialyse.backend.contexts.stock.publishedlanguage;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Langage publié du Stock à la Séance (port synchrone, ADR 0045) : les consommables consommés pendant une séance. Selon le paramètre
 * du centre, le Stock sort immédiatement les lots en FEFO (comportement historique) ou impute la consommation sur la sortie pour
 * séances du périmètre, sans mouvement de stock. Aucune dépendance vers le reste du contexte.
 *
 * @throws ConsommationRefusee (toutes les opérations) pour un refus métier
 */
public interface ConsommationsDeSeance {
    /**
     * Ajoute {@code quantite} d'un article aux consommables de la séance.
     */
    void ajouter(UUID centre, UUID seance, UUID patient, LocalDate dateSeance, UUID article, BigDecimal quantite, String auteur);

    /**
     * Fixe la quantité totale d'un article consommée pendant la séance ({@code 0} retire l'article).
     */
    void fixer(UUID centre, UUID seance, UUID patient, LocalDate dateSeance, UUID article, BigDecimal quantite, String auteur);

    /**
     * Annule toutes les consommations d'une séance supprimée. Refus si la séance est facturée.
     */
    void annuler(UUID centre, UUID seance, String auteur);

    /**
     * Consommation d'une administration de traitement (EPO, fer) à la date réelle de l'administration, tracée comme une sortie
     * distincte.
     */
    void administrer(UUID centre, UUID seance, UUID patient, LocalDate jour, UUID article, BigDecimal quantite, String auteur);

    /**
     * Consommables relevés à la validation de la séance.
     */
    void enregistrerALaValidation(UUID centre, UUID seance, UUID patient, LocalDate dateSeance, List<Ligne> lignes, String auteur);

    /**
     * Historique des consommables imputés sur la séance (sorties historiques + consommations imputées sur la pièce),
     * valorisés au PMP de sortie. Lecture pour l'affichage de la fiche séance (S9).
     */
    List<LigneHistorique> historique(UUID centre, UUID seance);

    /**
     * Sorties historiques (bons de sortie) rattachées à la séance, valorisées au PMP. Lecture pour l'affichage de la fiche
     * séance (S9).
     */
    List<LigneHistorique> sortiesHistoriques(UUID centre, UUID seance);

    /**
     * Sorties de stock (bons de sortie) pour une date de séance donnée, agrégées par article. Lecture pour l'affichage du tableau
     * de bord (S9).
     */
    List<SortieParDate> sortiesParDate(UUID centre, LocalDate date);

    record Ligne(UUID article, BigDecimal quantite) {
    }

    record LigneHistorique(UUID articleId, String articleCode, String articleLibelle, String articleUnite,
                           BigDecimal quantite, BigDecimal valeurUnitaire, BigDecimal totalValorise) {
    }

    record SortieParDate(UUID articleId, String articleCode, String articleLibelle, BigDecimal quantiteTotale) {
    }
}