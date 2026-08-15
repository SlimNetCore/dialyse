package com.hemodialyse.backend.domain.comptabilite.entity;

import com.hemodialyse.backend.domain.comptabilite.valueobject.AxeAnalytique;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Ligne d'une écriture comptable.
 * Invariant : montantDebit ≥ 0, montantCredit ≥ 0, et exactement l'un des deux est non nul
 * (ou les deux à zéro pour une ligne d'extourne nulle — cas limite, non utilisé en v1).
 */
public class LigneEcriture {
    private final UUID id;
    private final String compteSCF;
    private final String libelleLigne;
    private final BigDecimal montantDebit;
    private final BigDecimal montantCredit;
    /**
     * Identifiant du tiers (centre payeur, patient…) — utilisé pour le lettrage.
     */
    private final UUID tiersId;
    private final List<AxeAnalytique> axes;

    public LigneEcriture(UUID id, String compteSCF, String libelleLigne,
                         BigDecimal montantDebit, BigDecimal montantCredit,
                         UUID tiersId, List<AxeAnalytique> axes) {
        if (id == null) throw new IllegalArgumentException("L'identifiant de ligne est obligatoire");
        if (compteSCF == null || compteSCF.isBlank())
            throw new IllegalArgumentException("Le compte SCF est obligatoire");
        if (montantDebit == null || montantDebit.signum() < 0)
            throw new IllegalArgumentException("Le débit ne peut pas être négatif");
        if (montantCredit == null || montantCredit.signum() < 0)
            throw new IllegalArgumentException("Le crédit ne peut pas être négatif");
        this.id = id;
        this.compteSCF = compteSCF.trim();
        this.libelleLigne = libelleLigne != null ? libelleLigne.trim() : "";
        this.montantDebit = montantDebit.setScale(2, java.math.RoundingMode.HALF_UP);
        this.montantCredit = montantCredit.setScale(2, java.math.RoundingMode.HALF_UP);
        this.tiersId = tiersId;
        this.axes = axes != null ? List.copyOf(axes) : List.of();
    }

    /**
     * Factory : ligne de débit.
     */
    public static LigneEcriture debit(String compteSCF, String libelle, BigDecimal montant,
                                      UUID tiersId, List<AxeAnalytique> axes) {
        return new LigneEcriture(UUID.randomUUID(), compteSCF, libelle, montant, BigDecimal.ZERO, tiersId, axes);
    }

    /**
     * Factory : ligne de crédit.
     */
    public static LigneEcriture credit(String compteSCF, String libelle, BigDecimal montant,
                                       UUID tiersId, List<AxeAnalytique> axes) {
        return new LigneEcriture(UUID.randomUUID(), compteSCF, libelle, BigDecimal.ZERO, montant, tiersId, axes);
    }

    public UUID getId() {
        return id;
    }

    public String getCompteSCF() {
        return compteSCF;
    }

    public String getLibelleLigne() {
        return libelleLigne;
    }

    public BigDecimal getMontantDebit() {
        return montantDebit;
    }

    public BigDecimal getMontantCredit() {
        return montantCredit;
    }

    public UUID getTiersId() {
        return tiersId;
    }

    public List<AxeAnalytique> getAxes() {
        return axes;
    }
}

