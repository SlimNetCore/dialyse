package com.hemodialyse.backend.domain.gmao.model;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Journal (append-only) des changements de statut d'un équipement.
 * Sert de base au calcul de l'indisponibilité (temps passé en maintenance/panne) — voir
 * {@link com.hemodialyse.backend.domain.gmao.service.IndisponibiliteCalculator}.
 * Simple entrée d'audit, pas un agrégat : aucune règle métier à protéger au-delà de sa création.
 */
public final class EquipementStatutHistorique {

    private final UUID id;
    private final UUID equipementId;
    private final UUID centreId;
    private final StatutEquipement statutPrecedent;
    private final StatutEquipement statutNouveau;
    private final String motif;
    private final LocalDateTime changedAt;
    private final UUID changedBy;

    private EquipementStatutHistorique(UUID id, UUID equipementId, UUID centreId,
                                       StatutEquipement statutPrecedent, StatutEquipement statutNouveau,
                                       String motif, LocalDateTime changedAt, UUID changedBy) {
        this.id = id;
        this.equipementId = equipementId;
        this.centreId = centreId;
        this.statutPrecedent = statutPrecedent;
        this.statutNouveau = statutNouveau;
        this.motif = motif;
        this.changedAt = changedAt;
        this.changedBy = changedBy;
    }

    public static EquipementStatutHistorique enregistrer(
            UUID equipementId, UUID centreId, StatutEquipement statutPrecedent, StatutEquipement statutNouveau,
            String motif, UUID changedBy) {
        if (equipementId == null || centreId == null || statutNouveau == null) {
            throw new IllegalArgumentException("Équipement, centre et nouveau statut requis");
        }
        return new EquipementStatutHistorique(
                UUID.randomUUID(), equipementId, centreId, statutPrecedent, statutNouveau,
                motif, LocalDateTime.now(), changedBy);
    }

    public static EquipementStatutHistorique reconstruct(
            UUID id, UUID equipementId, UUID centreId, StatutEquipement statutPrecedent,
            StatutEquipement statutNouveau, String motif, LocalDateTime changedAt, UUID changedBy) {
        return new EquipementStatutHistorique(id, equipementId, centreId, statutPrecedent, statutNouveau,
                motif, changedAt, changedBy);
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

    public StatutEquipement getStatutPrecedent() {
        return statutPrecedent;
    }

    public StatutEquipement getStatutNouveau() {
        return statutNouveau;
    }

    public String getMotif() {
        return motif;
    }

    public LocalDateTime getChangedAt() {
        return changedAt;
    }

    public UUID getChangedBy() {
        return changedBy;
    }
}
