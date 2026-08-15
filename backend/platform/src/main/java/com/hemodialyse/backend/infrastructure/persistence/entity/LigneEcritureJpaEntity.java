package com.hemodialyse.backend.infrastructure.persistence.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "lignes_ecriture")
public class LigneEcritureJpaEntity {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ecriture_id", nullable = false)
    private EcritureComptableJpaEntity ecriture;

    @Column(name = "compte_scf", nullable = false, length = 20)
    private String compteSCF;

    @Column(name = "libelle_ligne", length = 255)
    private String libelleLigne;

    @Column(name = "montant_debit", precision = 14, scale = 2)
    private BigDecimal montantDebit;

    @Column(name = "montant_credit", precision = 14, scale = 2)
    private BigDecimal montantCredit;

    @Column(name = "tiers_id", columnDefinition = "uuid")
    private UUID tiersId;

    /**
     * Axes analytiques sérialisés en JSON simple (ex: CENTRE:xxx,FORFAIT:yyy).
     */
    @Column(name = "axes_analytiques", length = 500)
    private String axesAnalytiques;

    // ─── Getters / Setters ───────────────────────────────────────────────────

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public EcritureComptableJpaEntity getEcriture() {
        return ecriture;
    }

    public void setEcriture(EcritureComptableJpaEntity ecriture) {
        this.ecriture = ecriture;
    }

    public String getCompteSCF() {
        return compteSCF;
    }

    public void setCompteSCF(String compteSCF) {
        this.compteSCF = compteSCF;
    }

    public String getLibelleLigne() {
        return libelleLigne;
    }

    public void setLibelleLigne(String libelleLigne) {
        this.libelleLigne = libelleLigne;
    }

    public BigDecimal getMontantDebit() {
        return montantDebit;
    }

    public void setMontantDebit(BigDecimal montantDebit) {
        this.montantDebit = montantDebit;
    }

    public BigDecimal getMontantCredit() {
        return montantCredit;
    }

    public void setMontantCredit(BigDecimal montantCredit) {
        this.montantCredit = montantCredit;
    }

    public UUID getTiersId() {
        return tiersId;
    }

    public void setTiersId(UUID tiersId) {
        this.tiersId = tiersId;
    }

    public String getAxesAnalytiques() {
        return axesAnalytiques;
    }

    public void setAxesAnalytiques(String axesAnalytiques) {
        this.axesAnalytiques = axesAnalytiques;
    }
}

