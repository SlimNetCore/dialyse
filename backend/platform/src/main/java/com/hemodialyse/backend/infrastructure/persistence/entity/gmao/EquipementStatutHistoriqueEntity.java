package com.hemodialyse.backend.infrastructure.persistence.entity.gmao;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Entité JPA pour le journal de statut des équipements GMAO (append-only — base du calcul
 * d'indisponibilité).
 */
@Entity
@Table(name = "gmao_equipement_statut_historique")
public class EquipementStatutHistoriqueEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID equipementId;

    @Column(nullable = false)
    private UUID centreId;

    @Column(length = 50)
    private String statutPrecedent;

    @Column(nullable = false, length = 50)
    private String statutNouveau;

    @Column(columnDefinition = "TEXT")
    private String motif;

    @Column(nullable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime changedAt;

    private UUID changedBy;

    public EquipementStatutHistoriqueEntity() {
    }

    public EquipementStatutHistoriqueEntity(UUID id, UUID equipementId, UUID centreId, String statutPrecedent,
                                            String statutNouveau, String motif, OffsetDateTime changedAt,
                                            UUID changedBy) {
        this.id = id;
        this.equipementId = equipementId;
        this.centreId = centreId;
        this.statutPrecedent = statutPrecedent;
        this.statutNouveau = statutNouveau;
        this.motif = motif;
        this.changedAt = changedAt;
        this.changedBy = changedBy;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getEquipementId() {
        return equipementId;
    }

    public void setEquipementId(UUID equipementId) {
        this.equipementId = equipementId;
    }

    public UUID getCentreId() {
        return centreId;
    }

    public void setCentreId(UUID centreId) {
        this.centreId = centreId;
    }

    public String getStatutPrecedent() {
        return statutPrecedent;
    }

    public void setStatutPrecedent(String statutPrecedent) {
        this.statutPrecedent = statutPrecedent;
    }

    public String getStatutNouveau() {
        return statutNouveau;
    }

    public void setStatutNouveau(String statutNouveau) {
        this.statutNouveau = statutNouveau;
    }

    public String getMotif() {
        return motif;
    }

    public void setMotif(String motif) {
        this.motif = motif;
    }

    public OffsetDateTime getChangedAt() {
        return changedAt;
    }

    public void setChangedAt(OffsetDateTime changedAt) {
        this.changedAt = changedAt;
    }

    public UUID getChangedBy() {
        return changedBy;
    }

    public void setChangedBy(UUID changedBy) {
        this.changedBy = changedBy;
    }
}
