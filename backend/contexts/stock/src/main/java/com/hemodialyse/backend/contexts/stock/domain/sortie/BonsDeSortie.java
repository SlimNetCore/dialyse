package com.hemodialyse.backend.contexts.stock.domain.sortie;

import com.hemodialyse.backend.contexts.stock.domain.CentreId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * Port sortant : les bons de sortie d'un centre (tables {@code bons_sortie} et {@code bons_sortie_lignes}).
 */
public interface BonsDeSortie {

    /**
     * Enregistre un bon neuf avec la référence {@code BS} suivante du centre, dans la transaction de l'appelant (un échec ne
     * consomme aucune référence). Retourne le bon relu.
     */
    BonDeSortie creer(CentreId centre, Function<String, BonDeSortie> fabrique);

    Optional<BonDeSortie> parId(CentreId centre, UUID id);

    /**
     * Les bons du centre, du plus récent au plus ancien.
     */
    List<BonDeSortie> tous(CentreId centre);

    /**
     * Applique la correction au bon verrouillé et l'enregistre (en-tête et lignes) ; vide si le bon est inconnu ou d'un autre
     * centre. Retourne le bon relu.
     */
    Optional<BonDeSortie> modifier(CentreId centre, UUID id, UnaryOperator<BonDeSortie> modification);
}

