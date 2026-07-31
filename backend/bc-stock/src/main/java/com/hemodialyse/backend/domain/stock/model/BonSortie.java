package com.hemodialyse.backend.domain.stock.model;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Bon de sortie (stock issue note, BS-xxxxx) linked to a hemodialysis seance.
 */
public class BonSortie {
    private final List<LigneSortie> lignes = new ArrayList<>();
    private UUID id;
    private UUID centerId;
    private String reference;
    private UUID seanceId;
    private UUID patientId;
    private String poste;
    private LocalDate dateSortie;
    private String createdBy;
    private OffsetDateTime createdAt;

    public BonSortie() {
    }

    public static BonSortie create(UUID centerId, String reference, UUID seanceId, UUID patientId,
                                   String poste, LocalDate dateSortie, String createdBy) {
        BonSortie bon = new BonSortie();
        bon.id = UUID.randomUUID();
        bon.centerId = centerId;
        bon.reference = reference;
        bon.seanceId = seanceId;
        bon.patientId = patientId;
        bon.poste = poste;
        bon.dateSortie = dateSortie != null ? dateSortie : LocalDate.now();
        bon.createdBy = createdBy;
        bon.createdAt = OffsetDateTime.now();
        return bon;
    }

    public void ajouterLigne(LigneSortie ligne) {
        this.lignes.add(ligne);
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

    public UUID getSeanceId() {
        return seanceId;
    }

    public void setSeanceId(UUID seanceId) {
        this.seanceId = seanceId;
    }

    public UUID getPatientId() {
        return patientId;
    }

    public void setPatientId(UUID patientId) {
        this.patientId = patientId;
    }

    public String getPoste() {
        return poste;
    }

    public void setPoste(String poste) {
        this.poste = poste;
    }

    public LocalDate getDateSortie() {
        return dateSortie;
    }

    public void setDateSortie(LocalDate dateSortie) {
        this.dateSortie = dateSortie;
    }

    public List<LigneSortie> getLignes() {
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

