package com.hemodialyse.backend.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "articles")
public class ArticleJpaEntity {
    @Id
    private UUID id;

    @Column(name = "center_id", nullable = false)
    private UUID centerId;

    @Column(name = "code", nullable = false)
    private String code;

    @Column(name = "libelle", nullable = false)
    private String libelle;

    @Column(name = "unite", nullable = false)
    private String unite;

    @Column(name = "stock_quantity", nullable = false)
    private BigDecimal stockQuantity;

    @Column(name = "seuil_alerte", nullable = false)
    private BigDecimal seuilAlerte;

    @Column(name = "pmp_courant")
    private BigDecimal pmpCourant;

    @Column(name = "gere_par_lot", nullable = false)
    private boolean gereParLot;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "type_traitement_anemie", length = 20)
    private String typeTraitementAnemie;

    @Column(name = "created_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime createdAt;

    @Column(name = "dci", length = 150)
    private String dci;

    @Column(name = "forme_galenique", length = 80)
    private String formeGalenique;

    @Column(name = "code_barres", length = 64)
    private String codeBarres;

    @Column(name = "reference_fabricant", length = 80)
    private String referenceFabricant;

    @Column(name = "unite_achat", length = 30)
    private String uniteAchat;

    @Column(name = "coefficient_achat", precision = 14, scale = 4)
    private BigDecimal coefficientAchat;

    @Column(name = "dosage_par_unite", precision = 14, scale = 4)
    private BigDecimal dosageParUnite;

    @Column(name = "unite_dosage", length = 20)
    private String uniteDosage;

    @Column(name = "fournisseur_id")
    private UUID fournisseurId;

    @Column(name = "tva_type_id")
    private UUID tvaTypeId;

    @Column(name = "prix_achat", precision = 14, scale = 4)
    private BigDecimal prixAchat;

    @Column(name = "stock_max", precision = 14, scale = 4)
    private BigDecimal stockMax;

    @Column(name = "peremption_obligatoire", nullable = false, columnDefinition = "boolean default false")
    private boolean peremptionObligatoire;

    @Column(name = "condition_conservation", length = 20)
    private String conditionConservation;

    @Column(name = "produit_dangereux", nullable = false, columnDefinition = "boolean default false")
    private boolean produitDangereux;

    @Column(name = "dechet_dasri", nullable = false, columnDefinition = "boolean default false")
    private boolean dechetDasri;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getCenterId() {
        return centerId;
    }

    public void setCenterId(UUID centerId) {
        this.centerId = centerId;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getLibelle() {
        return libelle;
    }

    public void setLibelle(String libelle) {
        this.libelle = libelle;
    }

    public String getUnite() {
        return unite;
    }

    public void setUnite(String unite) {
        this.unite = unite;
    }

    public BigDecimal getStockQuantity() {
        return stockQuantity;
    }

    public void setStockQuantity(BigDecimal stockQuantity) {
        this.stockQuantity = stockQuantity;
    }

    public BigDecimal getSeuilAlerte() {
        return seuilAlerte;
    }

    public void setSeuilAlerte(BigDecimal seuilAlerte) {
        this.seuilAlerte = seuilAlerte;
    }

    public BigDecimal getPmpCourant() {
        return pmpCourant;
    }

    public void setPmpCourant(BigDecimal pmpCourant) {
        this.pmpCourant = pmpCourant;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isGereParLot() {
        return gereParLot;
    }

    public void setGereParLot(boolean gereParLot) {
        this.gereParLot = gereParLot;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getTypeTraitementAnemie() {
        return typeTraitementAnemie;
    }

    public void setTypeTraitementAnemie(String typeTraitementAnemie) {
        this.typeTraitementAnemie = typeTraitementAnemie;
    }

    public String getDci() {
        return dci;
    }

    public void setDci(String dci) {
        this.dci = dci;
    }

    public String getFormeGalenique() {
        return formeGalenique;
    }

    public void setFormeGalenique(String formeGalenique) {
        this.formeGalenique = formeGalenique;
    }

    public String getCodeBarres() {
        return codeBarres;
    }

    public void setCodeBarres(String codeBarres) {
        this.codeBarres = codeBarres;
    }

    public String getReferenceFabricant() {
        return referenceFabricant;
    }

    public void setReferenceFabricant(String referenceFabricant) {
        this.referenceFabricant = referenceFabricant;
    }

    public String getUniteAchat() {
        return uniteAchat;
    }

    public void setUniteAchat(String uniteAchat) {
        this.uniteAchat = uniteAchat;
    }

    public BigDecimal getCoefficientAchat() {
        return coefficientAchat;
    }

    public void setCoefficientAchat(BigDecimal coefficientAchat) {
        this.coefficientAchat = coefficientAchat;
    }

    public BigDecimal getDosageParUnite() {
        return dosageParUnite;
    }

    public void setDosageParUnite(BigDecimal dosageParUnite) {
        this.dosageParUnite = dosageParUnite;
    }

    public String getUniteDosage() {
        return uniteDosage;
    }

    public void setUniteDosage(String uniteDosage) {
        this.uniteDosage = uniteDosage;
    }

    public UUID getFournisseurId() {
        return fournisseurId;
    }

    public void setFournisseurId(UUID fournisseurId) {
        this.fournisseurId = fournisseurId;
    }

    public UUID getTvaTypeId() {
        return tvaTypeId;
    }

    public void setTvaTypeId(UUID tvaTypeId) {
        this.tvaTypeId = tvaTypeId;
    }

    public BigDecimal getPrixAchat() {
        return prixAchat;
    }

    public void setPrixAchat(BigDecimal prixAchat) {
        this.prixAchat = prixAchat;
    }

    public BigDecimal getStockMax() {
        return stockMax;
    }

    public void setStockMax(BigDecimal stockMax) {
        this.stockMax = stockMax;
    }

    public boolean isPeremptionObligatoire() {
        return peremptionObligatoire;
    }

    public void setPeremptionObligatoire(boolean peremptionObligatoire) {
        this.peremptionObligatoire = peremptionObligatoire;
    }

    public String getConditionConservation() {
        return conditionConservation;
    }

    public void setConditionConservation(String conditionConservation) {
        this.conditionConservation = conditionConservation;
    }

    public boolean isProduitDangereux() {
        return produitDangereux;
    }

    public void setProduitDangereux(boolean produitDangereux) {
        this.produitDangereux = produitDangereux;
    }

    public boolean isDechetDasri() {
        return dechetDasri;
    }

    public void setDechetDasri(boolean dechetDasri) {
        this.dechetDasri = dechetDasri;
    }
}

