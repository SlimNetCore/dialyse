package com.hemodialyse.backend.contexts.gmao.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Modèle de persistance d'une ligne de l'historique de statuts : table {@code gmao_equipement_statut_historique}, la table
 * historique, avec les mêmes noms de colonnes que l'ancienne entité (ADR 0024). Append-only : jamais modifiée.
 */
@Entity
@Table(name = "gmao_equipement_statut_historique")
public class EquipementStatutHistoriqueJpa {

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
    private Instant changedAt;

    private UUID changedBy;

    protected EquipementStatutHistoriqueJpa() {
    }

    public EquipementStatutHistoriqueJpa(UUID id, UUID equipementId, UUID centreId, String statutPrecedent,
                                         String statutNouveau, String motif, Instant changedAt, UUID changedBy) {
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

    public UUID getEquipementId() {
        return equipementId;
    }

    public UUID getCentreId() {
        return centreId;
    }

    public String getStatutPrecedent() {
        return statutPrecedent;
    }

    public String getStatutNouveau() {
        return statutNouveau;
    }

    public String getMotif() {
        return motif;
    }

    public Instant getChangedAt() {
        return changedAt;
    }

    public UUID getChangedBy() {
        return changedBy;
    }
}
