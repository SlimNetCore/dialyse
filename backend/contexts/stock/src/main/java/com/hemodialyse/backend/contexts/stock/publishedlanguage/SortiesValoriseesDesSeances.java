package com.hemodialyse.backend.contexts.stock.publishedlanguage;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Langage publié du Stock à la Séance (DOC5a, ADR 0193) : les consommables sortis du stock pour une séance, tels que les MOUVEMENTS du
 * centre les ont enregistrés (sorties, moins les entrées et ajustements qui les annulent), regroupés par article et prix unitaire, valorisés
 * à ce prix. Lecture pour le cahier de dialyse imprimé. Distinct de {@link ConsommationsDeSeance#historique}, qui lit aussi les
 * consommations imputées sur la sortie pour séances (aucun mouvement) : le cahier n'a jamais imprimé celles-ci (reproduit).
 */
public interface SortiesValoriseesDesSeances {

    /**
     * Les sorties nettes de la séance du centre, dans l'ordre du code d'article puis du prix unitaire ; vide si aucune. Les entrées et
     * ajustements comptent en négatif ; tout autre type de mouvement est ignoré. Un article inconnu du centre garde sa ligne, sans code ni
     * libellé.
     */
    List<LigneDeSortie> pourLaSeance(UUID centre, UUID seance);

    record LigneDeSortie(UUID article, String code, String libelle, String unite, BigDecimal quantite, BigDecimal prixUnitaire,
                         BigDecimal totalValorise) {
    }
}
