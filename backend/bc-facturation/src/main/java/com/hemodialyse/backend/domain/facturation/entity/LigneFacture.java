package com.hemodialyse.backend.domain.facturation.entity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

public class LigneFacture {
    private final UUID forfaitId;
    private final String forfaitLabel;
    private final BigDecimal unitPriceHt;
    private final int seanceCount;
    private final BigDecimal lineHt;

    public LigneFacture(UUID forfaitId, String forfaitLabel, BigDecimal unitPriceHt, int seanceCount) {
        if (unitPriceHt == null || unitPriceHt.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Le prix unitaire HT est invalide");
        }
        if (seanceCount <= 0) {
            throw new IllegalArgumentException("Le nombre de seances doit etre strictement positif");
        }
        this.forfaitId = forfaitId;
        this.forfaitLabel = forfaitLabel == null ? "Forfait" : forfaitLabel;
        this.unitPriceHt = unitPriceHt.setScale(2, RoundingMode.HALF_UP);
        this.seanceCount = seanceCount;
        this.lineHt = this.unitPriceHt.multiply(BigDecimal.valueOf(seanceCount)).setScale(2, RoundingMode.HALF_UP);
    }

    public UUID getForfaitId() {
        return forfaitId;
    }

    public String getForfaitLabel() {
        return forfaitLabel;
    }

    public BigDecimal getUnitPriceHt() {
        return unitPriceHt;
    }

    public int getSeanceCount() {
        return seanceCount;
    }

    public BigDecimal getLineHt() {
        return lineHt;
    }
}

