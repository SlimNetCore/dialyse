package com.hemodialyse.backend.infrastructure.persistence.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "regles_tva")
public class RegleTVAJpaEntity {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "center_id", nullable = false, columnDefinition = "uuid")
    private UUID centerId;

    @Column(name = "type_prestation", nullable = false, length = 80)
    private String typePrestation;

    @Column(name = "taux_applique", precision = 6, scale = 2, nullable = false)
    private BigDecimal tauxApplique;

    @Column(name = "exonere", nullable = false)
    private boolean exonere;

    @Column(name = "date_debut_validite", nullable = false)
    private LocalDate dateDebutValidite;

    @Column(name = "date_fin_validite")
    private LocalDate dateFinValidite;

    @Column(name = "texte_reference", length = 500)
    private String texteReference;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "created_by", length = 120)
    private String createdBy;

    @PrePersist
    void prePersist() {
        createdAt = OffsetDateTime.now();
    }

    // ─── Getters / Setters ───────────────────────────────────────────────────

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

    public String getTypePrestation() {
        return typePrestation;
    }

    public void setTypePrestation(String typePrestation) {
        this.typePrestation = typePrestation;
    }

    public BigDecimal getTauxApplique() {
        return tauxApplique;
    }

    public void setTauxApplique(BigDecimal tauxApplique) {
        this.tauxApplique = tauxApplique;
    }

    public boolean isExonere() {
        return exonere;
    }

    public void setExonere(boolean exonere) {
        this.exonere = exonere;
    }

    public LocalDate getDateDebutValidite() {
        return dateDebutValidite;
    }

    public void setDateDebutValidite(LocalDate dateDebutValidite) {
        this.dateDebutValidite = dateDebutValidite;
    }

    public LocalDate getDateFinValidite() {
        return dateFinValidite;
    }

    public void setDateFinValidite(LocalDate dateFinValidite) {
        this.dateFinValidite = dateFinValidite;
    }

    public String getTexteReference() {
        return texteReference;
    }

    public void setTexteReference(String texteReference) {
        this.texteReference = texteReference;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }
}

