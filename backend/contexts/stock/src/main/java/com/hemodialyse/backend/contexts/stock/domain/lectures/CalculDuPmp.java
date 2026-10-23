package com.hemodialyse.backend.contexts.stock.domain.lectures;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Rejeu du PMP (poids moyen pondéré, RG-STK-060, 062) : une entrée recalcule le PMP, une sortie est valorisée au PMP courant et
 * ne le modifie pas ; un stock ramené à zéro garde le dernier PMP. Service de domaine pur.
 */
public final class CalculDuPmp {

    public static final int ECHELLE = 4;

    private CalculDuPmp() {
    }

    public static Etat appliquer(Etat etat, MouvementDeStock m) {
        BigDecimal q = m.quantite() != null ? m.quantite() : BigDecimal.ZERO;
        if (m.type() == MouvementDeStock.Type.RETOUR) {
            // Un retour (rapprochement d'une sortie pour séances) est revalorisé au PMP qui le précède et ne le modifie pas ;
            // sans PMP antérieur (stock à zéro), il vaut son prix de sortie enregistré, comme une entrée.
            BigDecimal prix = etat.pmp().signum() > 0 ? etat.pmp() : (m.prixUnitaire() != null ? m.prixUnitaire() : BigDecimal.ZERO);
            BigDecimal quantite = etat.quantite().add(q);
            BigDecimal valeur = etat.valeur().add(q.multiply(prix));
            BigDecimal pmp = etat.pmp().signum() > 0 ? etat.pmp()
                    : (quantite.signum() > 0 ? valeur.divide(quantite, ECHELLE, RoundingMode.HALF_UP) : BigDecimal.ZERO);
            return new Etat(quantite, valeur, pmp);
        }
        if (m.estUneEntree()) {
            BigDecimal quantite = etat.quantite().add(q);
            BigDecimal valeur = etat.valeur().add(q.multiply(m.prixUnitaire() != null ? m.prixUnitaire() : BigDecimal.ZERO));
            BigDecimal pmp = quantite.signum() > 0 ? valeur.divide(quantite, ECHELLE, RoundingMode.HALF_UP) : BigDecimal.ZERO;
            return new Etat(quantite, valeur, pmp);
        }
        BigDecimal quantite = etat.quantite().subtract(q);
        BigDecimal valeur = etat.valeur().subtract(q.multiply(etat.pmp()));
        if (quantite.signum() <= 0) {
            return new Etat(BigDecimal.ZERO, BigDecimal.ZERO, etat.pmp());
        }
        return new Etat(quantite, valeur, etat.pmp());
    }

    /**
     * L'état avant et après chaque mouvement, avec la formule lisible de l'étape.
     *
     * @param ordonnes mouvements déjà dans l'ordre du rejeu
     */
    public static List<Etape> expliquer(List<MouvementDeStock> ordonnes) {
        Etat etat = Etat.vide();
        List<Etape> etapes = new ArrayList<>();
        for (MouvementDeStock m : ordonnes) {
            Etat avant = etat;
            etat = appliquer(etat, m);
            etapes.add(new Etape(m, avant, etat, formule(m, avant, etat)));
        }
        return etapes;
    }

    private static String formule(MouvementDeStock m, Etat avant, Etat apres) {
        BigDecimal q = m.quantite() != null ? m.quantite() : BigDecimal.ZERO;
        if (m.type() == MouvementDeStock.Type.RETOUR) {
            return String.format("RETOUR : %s unite(s) reintegree(s) au PMP courant %s ; PMP = %s", q.toPlainString(),
                    avant.pmp().toPlainString(), apres.pmp().toPlainString());
        }
        if (m.estUneEntree()) {
            BigDecimal pu = m.prixUnitaire() != null ? m.prixUnitaire() : BigDecimal.ZERO;
            return String.format("ENTREE : PMP = (%s + %s x %s) / (%s + %s) = %s / %s = %s", avant.valeur().toPlainString(),
                    q.toPlainString(), pu.toPlainString(), avant.quantite().toPlainString(), q.toPlainString(),
                    apres.valeur().toPlainString(), apres.quantite().toPlainString(), apres.pmp().toPlainString());
        }
        return String.format("SORTIE : %s unite(s) valorisee(s) au PMP courant %s ; PMP inchange = %s", q.toPlainString(),
                avant.pmp().toPlainString(), apres.pmp().toPlainString());
    }

    public record Etat(BigDecimal quantite, BigDecimal valeur, BigDecimal pmp) {
        public static Etat vide() {
            return new Etat(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }
    }

    public record Etape(MouvementDeStock mouvement, Etat avant, Etat apres, String formule) {
    }
}

