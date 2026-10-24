package com.hemodialyse.backend.contexts.stock.domain.lectures;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Valorisation historique du stock d'un article sur une période (RG-STK-043), par rejeu de tous ses mouvements avec le calcul du
 * PMP ({@link CalculDuPmp}). Service de domaine pur.
 * <ul>
 *   <li>valeur de début = valeur du stock juste avant {@code debut} ; valeur de fin = valeur juste avant {@code finExclue} ;</li>
 *   <li>entrées = valeur des entrées (et ajustements valorisés) de la période ; sorties = quantités sorties valorisées au PMP qui
 *       précède chaque sortie ; un retour en stock est une sortie négative au même PMP ;</li>
 *   <li>autres variations = écart de réconciliation (comptages d'inventaire, ajustements sans prix, arrondis) :
 *       {@code fin − début − entrées + sorties}.</li>
 * </ul>
 * Un inventaire clôturé repart d'un stock de départ : au premier mouvement {@code INVENTAIRE} d'une série de mouvements
 * simultanés, l'état est remis à zéro avant de poser les quantités comptées (les lots suivants du même inventaire s'ajoutent).
 */
public final class ValorisationHistorique {

    private static final int ECHELLE = 2;

    private ValorisationHistorique() {
    }

    /**
     * @param mouvements tous les mouvements de l'article (y compris ceux d'un inventaire clôturé), dans l'ordre
     *                   {@link MouvementDeStock#CHRONOLOGIE}
     */
    public static Valorisation calculer(List<MouvementDeStock> mouvements, OffsetDateTime debut, OffsetDateTime finExclue) {
        CalculDuPmp.Etat etat = CalculDuPmp.Etat.vide();
        BigDecimal valeurDebut = BigDecimal.ZERO;
        BigDecimal entrees = BigDecimal.ZERO;
        BigDecimal sorties = BigDecimal.ZERO;
        boolean debutFige = false;
        MouvementDeStock precedent = null;

        for (MouvementDeStock m : mouvements) {
            OffsetDateTime le = m.creeLe();
            if (le != null && !le.isBefore(finExclue)) {
                break;
            }
            if (!debutFige && le != null && !le.isBefore(debut)) {
                valeurDebut = etat.valeur();
                debutFige = true;
            }
            if (m.type() == MouvementDeStock.Type.INVENTAIRE && debutDeSerieDInventaire(precedent, m)) {
                etat = CalculDuPmp.Etat.vide();
            }
            CalculDuPmp.Etat avant = etat;
            etat = CalculDuPmp.appliquer(etat, m);

            if (debutFige) {
                BigDecimal q = m.quantite() != null ? m.quantite() : BigDecimal.ZERO;
                switch (m.type()) {
                    case ENTREE -> entrees = entrees.add(q.multiply(prix(m)));
                    case RETOUR -> sorties = sorties.subtract(q.multiply(avant.pmp()));
                    case AJUSTEMENT -> {
                        if (m.prixUnitaire() != null) {
                            entrees = entrees.add(q.multiply(m.prixUnitaire()));
                        } else {
                            sorties = sorties.add(q.multiply(avant.pmp()));
                        }
                    }
                    case SORTIE -> sorties = sorties.add(q.multiply(avant.pmp()));
                    case INVENTAIRE -> {
                        // comptage : neutre pour les flux, absorbé dans « autres variations »
                    }
                }
            }
            precedent = m;
        }
        if (!debutFige) {
            valeurDebut = etat.valeur();
        }
        BigDecimal valeurFin = etat.valeur();
        BigDecimal autres = valeurFin.subtract(valeurDebut).subtract(entrees).add(sorties);
        return new Valorisation(arrondi(valeurDebut), arrondi(entrees), arrondi(sorties), arrondi(autres), arrondi(valeurFin),
                etat.quantite());
    }

    private static boolean debutDeSerieDInventaire(MouvementDeStock precedent, MouvementDeStock m) {
        return precedent == null || precedent.type() != MouvementDeStock.Type.INVENTAIRE || precedent.creeLe() == null
                || !precedent.creeLe().equals(m.creeLe());
    }

    private static BigDecimal prix(MouvementDeStock m) {
        return m.prixUnitaire() != null ? m.prixUnitaire() : BigDecimal.ZERO;
    }

    private static BigDecimal arrondi(BigDecimal v) {
        return v.setScale(ECHELLE, RoundingMode.HALF_UP);
    }

    /**
     * Valorisation d'un article sur la période (montants à 2 décimales).
     */
    public record Valorisation(BigDecimal valeurDebut, BigDecimal entrees, BigDecimal sorties, BigDecimal autresVariations,
                               BigDecimal valeurFin, BigDecimal quantiteFin) {
    }
}
