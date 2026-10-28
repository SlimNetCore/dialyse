package com.hemodialyse.backend.contexts.stock.domain.reception;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * Port sortant : les bons de réception d'un centre (tables {@code bons_reception} et {@code bons_reception_lignes}).
 */
public interface BonsDeReception {

    /**
     * Enregistre un bon neuf avec la référence {@code BR} suivante du centre, dans une seule transaction (un échec ne consomme
     * aucune référence). Retourne le bon relu.
     */
    BonDeReception creer(CentreId centre, Function<String, BonDeReception> fabrique);

    Optional<BonDeReception> parId(CentreId centre, UUID id);

    /**
     * Les bons du centre, du plus récent au plus ancien.
     */
    List<BonDeReception> tous(CentreId centre);

    /**
     * Applique la modification au bon verrouillé et l'enregistre (entête, statut, lignes avec leur lot) ; vide si le bon est
     * inconnu ou d'un autre centre. Retourne le bon relu.
     */
    Optional<BonDeReception> modifier(CentreId centre, UUID id, UnaryOperator<BonDeReception> modification);
}

