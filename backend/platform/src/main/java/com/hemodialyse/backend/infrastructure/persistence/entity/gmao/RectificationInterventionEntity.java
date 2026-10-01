package com.hemodialyse.backend.infrastructure.persistence.entity.gmao;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Rectification d'une intervention terminée (motif, auteur, date, clôture annulée) — journal append-only
 * rejoué avec l'intervention.
 */
@Entity
@Table(name = "gmao_rectifications_intervention")
public class RectificationInterventionEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID interventionId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String motif;

    @Column
    private UUID par;

    @Column(nullable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime le;

    @Column(columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime clotureAnterieureLe;

    @Column
    private UUID clotureAnterieurePar;

    public RectificationInterventionEntity() {
    }

    public RectificationInterventionEntity(UUID id, UUID interventionId, String motif, UUID par, OffsetDateTime le,
                                           OffsetDateTime clotureAnterieureLe, UUID clotureAnterieurePar) {
        this.id = id;
        this.interventionId = interventionId;
        this.motif = motif;
        this.par = par;
        this.le = le;
        this.clotureAnterieureLe = clotureAnterieureLe;
        this.clotureAnterieurePar = clotureAnterieurePar;
    }

    public UUID getId() {
        return id;
    }

    public UUID getInterventionId() {
        return interventionId;
    }

    public String getMotif() {
        return motif;
    }

    public UUID getPar() {
        return par;
    }

    public OffsetDateTime getLe() {
        return le;
    }

    public OffsetDateTime getClotureAnterieureLe() {
        return clotureAnterieureLe;
    }

    public UUID getClotureAnterieurePar() {
        return clotureAnterieurePar;
    }
}
