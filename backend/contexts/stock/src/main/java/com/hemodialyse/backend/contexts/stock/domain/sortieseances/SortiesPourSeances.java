package com.hemodialyse.backend.contexts.stock.domain.sortieseances;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * Port sortant : les sorties pour séances d'un centre (tables {@code sorties_pour_seances} et {@code sorties_pour_seances_lignes}).
 */
public interface SortiesPourSeances {
    /**
     * Enregistre une pièce neuve avec la référence {@code SPS} suivante du centre, dans la transaction de l'appelant. Retourne la
     * pièce relue.
     */
    SortiePourSeances creer(CentreId centre, Function<String, SortiePourSeances> fabrique);

    Optional<SortiePourSeances> parId(CentreId centre, UUID id);

    /**
     * Les pièces du centre, de la plus récente à la plus ancienne.
     */
    List<SortiePourSeances> toutes(CentreId centre);

    /**
     * La dernière pièce enregistrée du périmètre (tous jours confondus) ; vide s'il n'y en a pas.
     */
    Optional<SortiePourSeances> dernieresDuPerimetre(CentreId centre, PerimetreDeSortie perimetre);

    /**
     * Les pièces du jour (jour des séances visées), de la plus récente à la plus ancienne.
     */
    List<SortiePourSeances> duJour(CentreId centre, java.time.LocalDate jour);

    /**
     * La référence d'une sortie pour séances du centre non encore rapprochée (la plus ancienne), s'il y en a une.
     */
    Optional<String> premiereNonRapprochee(CentreId centre);

    /**
     * Relit la pièce en posant un verrou de ligne ({@code FOR UPDATE}) jusqu'à la fin de la transaction : une consommation et une
     * validation de rapprochement se sérialisent (T44).
     */
    Optional<SortiePourSeances> verrouiller(CentreId centre, UUID id);

    /**
     * Applique la modification à la pièce verrouillée ({@code FOR UPDATE}) et l'enregistre (en-tête et lignes) ; vide si la pièce est
     * inconnue ou d'un autre centre. Retourne la pièce relue.
     */
    Optional<SortiePourSeances> modifier(CentreId centre, UUID id, UnaryOperator<SortiePourSeances> modification);

    Consommations consommations(CentreId centre, UUID sortieId);

    /**
     * Totaux imputés par les consommations de séance sur cette pièce.
     *
     * @param parLot   total par lot de la pièce
     * @param horsLots total par article imputé sans lot (au-delà de la pièce ou article absent de la pièce)
     */
    record Consommations(Map<UUID, BigDecimal> parLot, Map<UUID, BigDecimal> horsLots) {
    }
}