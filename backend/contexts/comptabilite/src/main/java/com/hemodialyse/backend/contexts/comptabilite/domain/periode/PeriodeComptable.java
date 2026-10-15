package com.hemodialyse.backend.contexts.comptabilite.domain.periode;

import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;

import java.time.Instant;
import java.time.YearMonth;

/**
 * Période comptable : un mois d'un centre. Une fois clôturée (par un administrateur, RG-CPT-005), plus aucune écriture n'est
 * générée ni saisie sur le mois, et la période ne se rouvre pas. Les mois futurs, anciens ou sans écriture peuvent se clôturer
 * dans n'importe quel ordre (reproduit). Racine d'agrégat.
 */
public final class PeriodeComptable {

    private final CentreId centre;
    private final YearMonth mois;
    private boolean cloturee;
    private Instant clotureeLe;
    private String clotureePar;

    private PeriodeComptable(CentreId centre, YearMonth mois, boolean cloturee, Instant clotureeLe, String clotureePar) {
        this.centre = centre;
        this.mois = mois;
        this.cloturee = cloturee;
        this.clotureeLe = clotureeLe;
        this.clotureePar = clotureePar;
    }

    /**
     * Un mois qui n'a jamais été clôturé.
     */
    public static PeriodeComptable ouverte(CentreId centre, YearMonth mois) {
        return new PeriodeComptable(centre, mois, false, null, null);
    }

    /**
     * Un mois tel qu'il est enregistré ; une période close héritée peut n'avoir ni date ni auteur.
     */
    public static PeriodeComptable reconstituer(CentreId centre, YearMonth mois, boolean cloturee, Instant clotureeLe,
                                                String clotureePar) {
        return new PeriodeComptable(centre, mois, cloturee, clotureeLe, clotureePar);
    }

    /**
     * Clôture le mois, une seule fois ; l'auteur est l'utilisateur qui clôture.
     */
    public void cloturer(Instant le, String par) {
        if (cloturee) {
            throw new PeriodeDejaCloturee(mois);
        }
        if (par == null || par.isBlank()) {
            throw new IllegalArgumentException("L'auteur de la clôture est obligatoire");
        }
        cloturee = true;
        clotureeLe = le;
        clotureePar = par;
    }

    public CentreId centre() {
        return centre;
    }

    public YearMonth mois() {
        return mois;
    }

    public boolean estCloturee() {
        return cloturee;
    }

    public Instant clotureeLe() {
        return clotureeLe;
    }

    public String clotureePar() {
        return clotureePar;
    }
}
