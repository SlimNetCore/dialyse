package com.hemodialyse.backend.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Paramétrage du planning d'un centre : jours d'ouverture et salles d'isolement (listes séparées par des virgules).
 */
@Entity
@Table(name = "planning_parametres")
public class PlanningParametresJpaEntity {

    @Id
    @Column(name = "center_id")
    private UUID centerId;

    @Column(name = "jours_ouverts", nullable = false, length = 100)
    private String joursOuverts;

    @Column(name = "salles_isolement", columnDefinition = "TEXT")
    private String sallesIsolement;

    @Column(name = "updated_at", nullable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime updatedAt;

    public PlanningParametresJpaEntity() {
    }

    public PlanningParametresJpaEntity(UUID centerId, String joursOuverts, String sallesIsolement,
                                       OffsetDateTime updatedAt) {
        this.centerId = centerId;
        this.joursOuverts = joursOuverts;
        this.sallesIsolement = sallesIsolement;
        this.updatedAt = updatedAt;
    }

    public UUID getCenterId() {
        return centerId;
    }

    public String getJoursOuverts() {
        return joursOuverts;
    }

    public String getSallesIsolement() {
        return sallesIsolement;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
