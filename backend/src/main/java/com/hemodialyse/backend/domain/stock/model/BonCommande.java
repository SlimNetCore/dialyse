package com.hemodialyse.backend.domain.stock.model;

import com.hemodialyse.backend.domain.shared.vo.Money;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Bon de commande (purchase order, BL-xxxxx).
 */
public class BonCommande {
    private final List<LigneBonCommande> lignes = new ArrayList<>();
    private UUID id;
    private UUID centerId;
    private String reference;
    private UUID fournisseurId;
    private BonStatut statut;
    private String createdBy;
    private OffsetDateTime createdAt;

    public BonCommande() {
    }

    public static BonCommande brouillon(UUID centerId, String reference, UUID fournisseurId, String createdBy) {
        BonCommande bon = new BonCommande();
        bon.id = UUID.randomUUID();
        bon.centerId = centerId;
        bon.reference = reference;
        bon.fournisseurId = fournisseurId;
        bon.statut = BonStatut.BROUILLON;
        bon.createdBy = createdBy;
        bon.createdAt = OffsetDateTime.now();
        return bon;
    }

    public void valider() {
        if (statut != BonStatut.BROUILLON) {
            throw new IllegalStateException("Seul un bon BROUILLON peut etre valide");
        }
        if (lignes.isEmpty()) {
            throw new IllegalStateException("Un bon de commande doit contenir au moins une ligne");
        }
        this.statut = BonStatut.VALIDE;
    }

    public void marquerRecu() {
        this.statut = BonStatut.RECU;
    }

    public void remplacerLignes(List<LigneBonCommande> nouvelles) {
        this.lignes.clear();
        if (nouvelles != null) {
            this.lignes.addAll(nouvelles);
        }
    }

    public BigDecimal total() {
        return lignes.stream()
                .map(ligne -> Money.of(ligne.sousTotal()))
                .reduce(Money.zero(), Money::add)
                .amount();
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

    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

    public UUID getFournisseurId() {
        return fournisseurId;
    }

    public void setFournisseurId(UUID fournisseurId) {
        this.fournisseurId = fournisseurId;
    }

    public BonStatut getStatut() {
        return statut;
    }

    public void setStatut(BonStatut statut) {
        this.statut = statut;
    }

    public List<LigneBonCommande> getLignes() {
        return lignes;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}

