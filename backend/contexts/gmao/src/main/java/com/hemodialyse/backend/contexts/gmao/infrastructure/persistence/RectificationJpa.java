package com.hemodialyse.backend.contexts.gmao.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Modèle de persistance d'une rectification : table {@code gmao_rectifications_intervention}, mêmes colonnes que l'ancienne entité. */
@Entity
@Table(name = "gmao_rectifications_intervention")
public class RectificationJpa {

    @Id
    UUID id;

    @Column(nullable = false)
    UUID interventionId;

    @Column(nullable = false, columnDefinition = "TEXT")
    String motif;

    @Column
    UUID par;

    @Column(nullable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    Instant le;

    @Column(columnDefinition = "TIMESTAMP WITH TIME ZONE")
    Instant clotureAnterieureLe;

    @Column
    UUID clotureAnterieurePar;

    protected RectificationJpa() {
    }

    RectificationJpa(UUID id) {
        this.id = id;
    }
}
