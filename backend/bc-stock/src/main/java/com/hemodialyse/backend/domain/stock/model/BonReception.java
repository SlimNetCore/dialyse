package com.hemodialyse.backend.domain.stock.model;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Bon de reception (goods receipt note, BR-xxxxx). Creates lots and triggers PMP.
 */
public class BonReception {
    private final List<LigneReception> lignes = new ArrayList<>();
    private UUID id;
    private UUID centerId;
    private String reference;
    private UUID bonCommandeId;
    private UUID fournisseurId;
    private LocalDate dateReception;
    private BonStatut statut;
    private String createdBy;
    private OffsetDateTime createdAt;

    public BonReception() {
    }

    public static BonReception brouillon(UUID centerId, String reference, UUID bonCommandeId,
                                         UUID fournisseurId, LocalDate dateReception, String createdBy) {
        BonReception bon = new BonReception();
        bon.id = UUID.randomUUID();
        bon.centerId = centerId;
        bon.reference = reference;
        bon.bonCommandeId = bonCommandeId;
        bon.fournisseurId = fournisseurId;
        bon.dateReception = dateReception != null ? dateReception : LocalDate.now();
        bon.statut = BonStatut.BROUILLON;
        bon.createdBy = createdBy;
        bon.createdAt = OffsetDateTime.now();
        return bon;
    }

    public void valider() {
        if (statut != BonStatut.BROUILLON) {
            throw new IllegalStateException("Seul un bon de reception BROUILLON peut etre valide");
        }
        if (lignes.isEmpty()) {
            throw new IllegalStateException("Un bon de reception doit contenir au moins une ligne");
        }
        this.statut = BonStatut.VALIDE;
    }

    public void remplacerLignes(List<LigneReception> nouvelles) {
        this.lignes.clear();
        if (nouvelles != null) {
            this.lignes.addAll(nouvelles);
        }
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

    public UUID getBonCommandeId() {
        return bonCommandeId;
    }

    public void setBonCommandeId(UUID bonCommandeId) {
        this.bonCommandeId = bonCommandeId;
    }

    public UUID getFournisseurId() {
        return fournisseurId;
    }

    public void setFournisseurId(UUID fournisseurId) {
        this.fournisseurId = fournisseurId;
    }

    public LocalDate getDateReception() {
        return dateReception;
    }

    public void setDateReception(LocalDate dateReception) {
        this.dateReception = dateReception;
    }

    public BonStatut getStatut() {
        return statut;
    }

    public void setStatut(BonStatut statut) {
        this.statut = statut;
    }

    public List<LigneReception> getLignes() {
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

