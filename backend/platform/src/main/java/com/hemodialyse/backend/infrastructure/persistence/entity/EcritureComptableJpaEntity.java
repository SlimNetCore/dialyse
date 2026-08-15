package com.hemodialyse.backend.infrastructure.persistence.entity;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "ecritures_comptables",
        indexes = @Index(name = "idx_ecriture_center_period", columnList = "center_id, journal_code, date_ecriture"))
public class EcritureComptableJpaEntity {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "center_id", nullable = false, columnDefinition = "uuid")
    private UUID centerId;

    @Column(name = "journal_code", nullable = false, length = 10)
    private String journalCode;

    @Column(name = "date_ecriture", nullable = false)
    private LocalDate dateEcriture;

    @Column(name = "date_piece", nullable = false)
    private LocalDate datePiece;

    @Column(name = "numero_piece", nullable = false, length = 30)
    private String numeroPiece;

    @Column(name = "libelle", length = 255)
    private String libelle;

    @Column(name = "statut", nullable = false, length = 20)
    private String statut;

    @Column(name = "source_id", columnDefinition = "uuid")
    private UUID sourceId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @OneToMany(mappedBy = "ecriture", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("id ASC")
    private List<LigneEcritureJpaEntity> lignes = new ArrayList<>();

    @PrePersist
    void prePersist() {
        createdAt = OffsetDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = OffsetDateTime.now();
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

    public String getJournalCode() {
        return journalCode;
    }

    public void setJournalCode(String journalCode) {
        this.journalCode = journalCode;
    }

    public LocalDate getDateEcriture() {
        return dateEcriture;
    }

    public void setDateEcriture(LocalDate dateEcriture) {
        this.dateEcriture = dateEcriture;
    }

    public LocalDate getDatePiece() {
        return datePiece;
    }

    public void setDatePiece(LocalDate datePiece) {
        this.datePiece = datePiece;
    }

    public String getNumeroPiece() {
        return numeroPiece;
    }

    public void setNumeroPiece(String numeroPiece) {
        this.numeroPiece = numeroPiece;
    }

    public String getLibelle() {
        return libelle;
    }

    public void setLibelle(String libelle) {
        this.libelle = libelle;
    }

    public String getStatut() {
        return statut;
    }

    public void setStatut(String statut) {
        this.statut = statut;
    }

    public UUID getSourceId() {
        return sourceId;
    }

    public void setSourceId(UUID sourceId) {
        this.sourceId = sourceId;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public List<LigneEcritureJpaEntity> getLignes() {
        return lignes;
    }

    public void setLignes(List<LigneEcritureJpaEntity> lignes) {
        this.lignes = lignes;
    }
}




