package com.hemodialyse.backend.contexts.stock.domain.referentiel;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Article du catalogue d'un centre (agrégat, RG-STK-001 à 008, ADR 0006) : sa fiche et son activité. Le stock courant et le
 * PMP courant sont portés en lecture seule (ils résultent des mouvements, slices S5 à S7) : aucune commande du référentiel ne
 * les modifie. Un article référencé naît actif, stock et PMP à 0.
 */
public final class Article {

    private final UUID id;
    private final CentreId centre;
    private final OffsetDateTime creeLe;
    private final BigDecimal stock;
    private final BigDecimal pmp;
    private FicheArticle fiche;
    private boolean actif;

    private Article(UUID id, CentreId centre, FicheArticle fiche, boolean actif, BigDecimal stock, BigDecimal pmp,
                    OffsetDateTime creeLe) {
        this.id = Objects.requireNonNull(id, "L'identifiant est obligatoire");
        this.centre = Objects.requireNonNull(centre, "Le centre est obligatoire");
        this.fiche = Objects.requireNonNull(fiche, "La fiche est obligatoire");
        this.actif = actif;
        this.stock = stock;
        this.pmp = pmp;
        this.creeLe = creeLe;
    }

    /**
     * Un nouvel article du centre : actif, stock et PMP à 0.
     */
    public static Article referencer(UUID id, CentreId centre, FicheArticle fiche, OffsetDateTime creeLe) {
        return new Article(id, centre, fiche, true, BigDecimal.ZERO, BigDecimal.ZERO, creeLe);
    }

    /**
     * Un article tel qu'il est enregistré.
     */
    public static Article reconstituer(UUID id, CentreId centre, FicheArticle fiche, boolean actif, BigDecimal stock,
                                       BigDecimal pmp, OffsetDateTime creeLe) {
        return new Article(id, centre, fiche, actif, stock, pmp, creeLe);
    }

    /**
     * Remplace la fiche ; le stock, le PMP et l'activité ne changent pas.
     */
    public void modifierFiche(FicheArticle nouvelle) {
        this.fiche = Objects.requireNonNull(nouvelle, "La fiche est obligatoire");
    }

    public void changerActivite(boolean actif) {
        this.actif = actif;
    }

    public UUID id() {
        return id;
    }

    public CentreId centre() {
        return centre;
    }

    public FicheArticle fiche() {
        return fiche;
    }

    public boolean actif() {
        return actif;
    }

    public BigDecimal stock() {
        return stock;
    }

    public BigDecimal pmp() {
        return pmp;
    }

    public OffsetDateTime creeLe() {
        return creeLe;
    }
}

