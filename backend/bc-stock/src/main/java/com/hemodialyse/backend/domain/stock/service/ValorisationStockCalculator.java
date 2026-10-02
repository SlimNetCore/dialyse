package com.hemodialyse.backend.domain.stock.service;

import com.hemodialyse.backend.domain.stock.model.StockMovement;
import com.hemodialyse.backend.domain.stock.model.StockMovementType;
import com.hemodialyse.backend.domain.stock.service.PmpCalculator.PmpState;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Valorisation historique du stock d'un article sur une période, par rejeu de ses mouvements (méthode du
 * PMP, {@link PmpCalculator}). Service de domaine pur.
 * <ul>
 *   <li>valeur de début = valeur du stock juste avant {@code debut} ; valeur de fin = valeur juste avant
 *       {@code finExclue} ;</li>
 *   <li>entrées = valeur des entrées (et ajustements valorisés) de la période ; sorties = quantités sorties
 *       valorisées au PMP qui précède chaque sortie ;</li>
 *   <li>autres variations = écart de réconciliation (comptages d'inventaire, ajustements sans prix,
 *       arrondis) : {@code fin − début − entrées + sorties}.</li>
 * </ul>
 * Un inventaire clôturé repart d'un stock de départ : au premier mouvement INVENTAIRE d'un lot de mouvements
 * simultanés, l'état est remis à zéro avant de poser les quantités comptées.
 */
public final class ValorisationStockCalculator {

    private static final int SCALE = 2;

    private ValorisationStockCalculator() {
    }

    /**
     * @param mouvements mouvements d'un article, triés par date puis identifiant
     */
    public static Valorisation calculer(List<StockMovement> mouvements, OffsetDateTime debut, OffsetDateTime finExclue) {
        PmpState etat = PmpState.empty();
        BigDecimal valeurDebut = BigDecimal.ZERO;
        BigDecimal entrees = BigDecimal.ZERO;
        BigDecimal sorties = BigDecimal.ZERO;
        boolean debutFige = false;
        StockMovement precedent = null;

        for (StockMovement m : mouvements) {
            OffsetDateTime at = m.getCreatedAt();
            if (at != null && !at.isBefore(finExclue)) break;
            if (!debutFige && at != null && !at.isBefore(debut)) {
                valeurDebut = etat.valeur();
                debutFige = true;
            }
            boolean dansPeriode = debutFige;

            // Premier comptage d'un inventaire : le stock repart de zéro ; les suivants (autres lots du même
            // inventaire) s'ajoutent sans remise à zéro.
            if (m.getMovementType() == StockMovementType.INVENTAIRE && debutSerieInventaire(precedent, m)) {
                etat = PmpState.empty();
            }

            PmpState avant = etat;
            etat = PmpCalculator.apply(etat, m);

            if (dansPeriode) {
                BigDecimal q = m.getQuantite() != null ? m.getQuantite() : BigDecimal.ZERO;
                switch (m.getMovementType()) {
                    case ENTREE -> entrees = entrees.add(q.multiply(prix(m)));
                    case AJUSTEMENT -> {
                        if (m.getPrixUnitaire() != null) entrees = entrees.add(q.multiply(m.getPrixUnitaire()));
                        else sorties = sorties.add(q.multiply(avant.pmp()));
                    }
                    case SORTIE -> sorties = sorties.add(q.multiply(avant.pmp()));
                    case INVENTAIRE -> {
                        // comptage : neutre pour les flux, absorbé dans « autres variations »
                    }
                }
            }
            precedent = m;
        }
        if (!debutFige) valeurDebut = etat.valeur();

        BigDecimal valeurFin = etat.valeur();
        BigDecimal autres = valeurFin.subtract(valeurDebut).subtract(entrees).add(sorties);
        return new Valorisation(
                arrondi(valeurDebut), arrondi(entrees), arrondi(sorties), arrondi(autres), arrondi(valeurFin),
                etat.quantite());
    }

    /**
     * Premier mouvement INVENTAIRE d'un lot : le précédent n'est pas un INVENTAIRE de même horodatage.
     */
    private static boolean debutSerieInventaire(StockMovement precedent, StockMovement m) {
        return precedent == null
                || precedent.getMovementType() != StockMovementType.INVENTAIRE
                || precedent.getCreatedAt() == null
                || !precedent.getCreatedAt().equals(m.getCreatedAt());
    }

    private static BigDecimal prix(StockMovement m) {
        return m.getPrixUnitaire() != null ? m.getPrixUnitaire() : BigDecimal.ZERO;
    }

    private static BigDecimal arrondi(BigDecimal v) {
        return v.setScale(SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Valorisation d'un article, ou somme de plusieurs (voir {@link #plus}).
     */
    public record Valorisation(
            BigDecimal valeurDebut,
            BigDecimal entrees,
            BigDecimal sorties,
            BigDecimal autresVariations,
            BigDecimal valeurFin,
            BigDecimal quantiteFin
    ) {
        public static Valorisation zero() {
            BigDecimal z = BigDecimal.ZERO.setScale(SCALE);
            return new Valorisation(z, z, z, z, z, BigDecimal.ZERO);
        }

        public Valorisation plus(Valorisation o) {
            return new Valorisation(valeurDebut.add(o.valeurDebut), entrees.add(o.entrees), sorties.add(o.sorties),
                    autresVariations.add(o.autresVariations), valeurFin.add(o.valeurFin),
                    quantiteFin.add(o.quantiteFin));
        }
    }
}
