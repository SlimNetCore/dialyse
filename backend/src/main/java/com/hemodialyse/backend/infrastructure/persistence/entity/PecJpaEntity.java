package com.hemodialyse.backend.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity @Table(name = "prise_en_charge")
public class PecJpaEntity {
    @Id private UUID id;
    @Column(name = "patient_id", nullable = false) private UUID patientId;
    @Column(name = "center_id", nullable = false) private UUID centerId;
    @Column(name = "date_debut_demande") private LocalDate dateDebutDemande;
    @Column(name = "date_fin_demande") private LocalDate dateFinDemande;
    @Column(name = "forfait_demande_id") private UUID forfaitDemandeId;
    @Column(name = "date_debut_effectif") private LocalDate dateDebutEffectif;
    @Column(name = "date_fin_effectif") private LocalDate dateFinEffectif;
    @Column(name = "forfait_effectif_id") private UUID forfaitEffectifId;
    @Enumerated(EnumType.STRING) @Column(name = "statut", nullable = false) private String statut;
    @Column(name = "created_at") private OffsetDateTime createdAt;

    public UUID getId() { return id; } public void setId(UUID v) { this.id = v; }
    public UUID getPatientId() { return patientId; } public void setPatientId(UUID v) { this.patientId = v; }
    public UUID getCenterId() { return centerId; } public void setCenterId(UUID v) { this.centerId = v; }
    public LocalDate getDateDebutDemande() { return dateDebutDemande; } public void setDateDebutDemande(LocalDate v) { this.dateDebutDemande = v; }
    public LocalDate getDateFinDemande() { return dateFinDemande; } public void setDateFinDemande(LocalDate v) { this.dateFinDemande = v; }
    public UUID getForfaitDemandeId() { return forfaitDemandeId; } public void setForfaitDemandeId(UUID v) { this.forfaitDemandeId = v; }
    public LocalDate getDateDebutEffectif() { return dateDebutEffectif; } public void setDateDebutEffectif(LocalDate v) { this.dateDebutEffectif = v; }
    public LocalDate getDateFinEffectif() { return dateFinEffectif; } public void setDateFinEffectif(LocalDate v) { this.dateFinEffectif = v; }
    public UUID getForfaitEffectifId() { return forfaitEffectifId; } public void setForfaitEffectifId(UUID v) { this.forfaitEffectifId = v; }
    public String getStatut() { return statut; } public void setStatut(String v) { this.statut = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; } public void setCreatedAt(OffsetDateTime v) { this.createdAt = v; }
}

