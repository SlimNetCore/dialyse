package com.hemodialyse.backend.contexts.stock.domain.referentiel;

import com.hemodialyse.backend.contexts.stock.domain.CentreId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port sortant : les articles d'un centre (table {@code articles}).
 */
public interface Articles {

    /**
     * L'article du centre ; vide s'il est inconnu ou d'un autre centre.
     */
    Optional<Article> parId(CentreId centre, UUID id);

    /**
     * Un article du centre portant ce code, sans tenir compte de la casse (RG-STK-007).
     */
    Optional<Article> parCode(CentreId centre, String code);

    /**
     * Enregistre un article nouveau (stock et PMP à 0) ou la fiche et l'activité d'un article existant ; le stock et le PMP
     * enregistrés ne sont jamais réécrits par le référentiel.
     */
    void enregistrer(Article article);

    /**
     * Recherche paginée : texte (code, libellé, DCI ou code-barres, sans casse, contient), activité facultative ; tri par code.
     */
    PageDArticles rechercher(CentreId centre, String texte, Boolean actif, Pagination pagination);

    /**
     * Tous les articles du centre, actifs et inactifs, triés par code.
     */
    List<Article> tous(CentreId centre);

    /**
     * Les articles du centre d'un type de traitement de l'anémie, triés par code.
     */
    List<Article> parTypeDeTraitement(CentreId centre, TypeTraitementAnemie type);
}

