package com.hemodialyse.backend.domain.article.model;

import com.hemodialyse.backend.domain.shared.exception.BusinessException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.UUID;

public class Article {
    private UUID id;
    private UUID centerId;
    private String code;
    private String libelle;
    private String unite;
    private BigDecimal stockQuantity;
    private BigDecimal seuilAlerte;
    private BigDecimal pmpCourant;
    private boolean gereParLot;
    private boolean active;
    private TypeTraitementAnemie typeTraitementAnemie;
    private OffsetDateTime createdAt;

    // --- Fiche article complète ---
    private String dci;
    private String formeGalenique;
    private String codeBarres;
    private String referenceFabricant;
    private String uniteAchat;
    private BigDecimal coefficientAchat;
    private BigDecimal dosageParUnite;
    private String uniteDosage;
    private UUID fournisseurId;
    private UUID tvaTypeId;
    private BigDecimal prixAchat;
    private BigDecimal stockMax;
    private boolean peremptionObligatoire;
    private ConditionConservation conditionConservation;
    private boolean produitDangereux;
    private boolean dechetDasri;

    private static boolean sameUnit(String a, String b) {
        return a != null && b != null && a.trim().equalsIgnoreCase(b.trim());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean isNegative(BigDecimal value) {
        return value != null && value.signum() < 0;
    }

    private static String clean(String value) {
        return isBlank(value) ? null : value.trim();
    }

    /**
     * Applique les données de la fiche en protégeant les invariants : champs obligatoires, dosage et unité de dosage
     * indissociables, coefficients strictement positifs, stock maximum au moins égal au seuil d'alerte.
     */
    public void appliquerFiche(ArticleFiche fiche) {
        if (isBlank(fiche.code())) {
            throw new BusinessException("ARTICLE_CODE_REQUIS", "Le code article est obligatoire");
        }
        if (isBlank(fiche.libelle())) {
            throw new BusinessException("ARTICLE_LIBELLE_REQUIS", "Le libellé article est obligatoire");
        }
        if (isBlank(fiche.unite())) {
            throw new BusinessException("ARTICLE_UNITE_REQUISE", "L'unité de stock est obligatoire");
        }
        boolean dosage = fiche.dosageParUnite() != null;
        if (dosage == isBlank(fiche.uniteDosage())) {
            throw new BusinessException("ARTICLE_DOSAGE_INCOMPLET",
                    "Le dosage par unité et son unité (UI, mg…) vont ensemble");
        }
        if (dosage && fiche.dosageParUnite().signum() <= 0) {
            throw new BusinessException("ARTICLE_DOSAGE_INVALIDE", "Le dosage par unité doit être strictement positif");
        }
        if (fiche.coefficientAchat() != null && fiche.coefficientAchat().signum() <= 0) {
            throw new BusinessException("ARTICLE_COEFFICIENT_ACHAT_INVALIDE",
                    "Le coefficient d'achat doit être strictement positif");
        }
        if (isNegative(fiche.prixAchat()) || isNegative(fiche.seuilAlerte()) || isNegative(fiche.stockMax())) {
            throw new BusinessException("ARTICLE_VALEUR_NEGATIVE", "Prix, seuil et stock maximum ne peuvent être négatifs");
        }
        BigDecimal seuil = fiche.seuilAlerte() != null ? fiche.seuilAlerte() : BigDecimal.ZERO;
        if (fiche.stockMax() != null && fiche.stockMax().compareTo(seuil) < 0) {
            throw new BusinessException("ARTICLE_STOCK_MAX_INFERIEUR_SEUIL",
                    "Le stock maximum ne peut être inférieur au seuil d'alerte");
        }
        this.code = fiche.code().trim();
        this.libelle = fiche.libelle().trim();
        this.dci = clean(fiche.dci());
        this.formeGalenique = clean(fiche.formeGalenique());
        this.codeBarres = clean(fiche.codeBarres());
        this.referenceFabricant = clean(fiche.referenceFabricant());
        this.unite = fiche.unite().trim();
        this.uniteAchat = clean(fiche.uniteAchat());
        this.coefficientAchat = fiche.coefficientAchat();
        this.dosageParUnite = fiche.dosageParUnite();
        this.uniteDosage = clean(fiche.uniteDosage());
        this.fournisseurId = fiche.fournisseurId();
        this.tvaTypeId = fiche.tvaTypeId();
        this.prixAchat = fiche.prixAchat();
        this.seuilAlerte = seuil;
        this.stockMax = fiche.stockMax();
        this.gereParLot = fiche.gereParLot();
        this.peremptionObligatoire = fiche.peremptionObligatoire();
        this.conditionConservation = fiche.conditionConservation();
        this.produitDangereux = fiche.produitDangereux();
        this.dechetDasri = fiche.dechetDasri();
        this.typeTraitementAnemie = fiche.typeTraitementAnemie();
    }

    /**
     * Convertit une dose prescrite (UI, mg…) en quantité à sortir du stock, exprimée dans l'unité de stock :
     * {@code dose ÷ dosageParUnite}. Sans dosage défini, seule une dose déjà exprimée dans l'unité de stock est
     * acceptée (conversion 1:1) ; toute autre unité est refusée plutôt que de décrémenter le stock au hasard.
     */
    public BigDecimal quantiteStockPourDose(BigDecimal dose, String uniteDose) {
        if (dose == null || dose.signum() <= 0) {
            throw new BusinessException("DOSE_ADMINISTREE_INVALIDE", "La dose administrée doit être strictement positive");
        }
        if (dosageParUnite != null) {
            if (!sameUnit(uniteDose, uniteDosage)) {
                throw new BusinessException("ARTICLE_UNITE_DOSE_INCOMPATIBLE",
                        "L'article " + code + " se dose en " + uniteDosage + ", pas en " + uniteDose);
            }
            return dose.divide(dosageParUnite, 4, RoundingMode.HALF_UP);
        }
        if (sameUnit(uniteDose, unite)) {
            return dose;
        }
        throw new BusinessException("ARTICLE_DOSAGE_NON_DEFINI",
                "Le dosage par unité de l'article " + code + " n'est pas renseigné : conversion " + uniteDose
                        + " → " + unite + " impossible");
    }

    public void debiter(BigDecimal quantite) {
        if (quantite == null || quantite.signum() <= 0) {
            throw new IllegalArgumentException("La quantite consommee doit etre strictement positive");
        }
        BigDecimal current = stockQuantity != null ? stockQuantity : BigDecimal.ZERO;
        if (current.compareTo(quantite) < 0) {
            throw new IllegalStateException("Stock insuffisant pour l'article " + (code != null ? code : id));
        }
        this.stockQuantity = current.subtract(quantite);
    }

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

    public TypeTraitementAnemie getTypeTraitementAnemie() {
        return typeTraitementAnemie;
    }

    public void setTypeTraitementAnemie(TypeTraitementAnemie typeTraitementAnemie) {
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

    public ConditionConservation getConditionConservation() {
        return conditionConservation;
    }

    public void setConditionConservation(ConditionConservation conditionConservation) {
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

