package com.hemodialyse.backend.contexts.gmao.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Modèle de persistance d'une tâche d'intervention : table {@code gmao_taches_intervention} (créée par V5, jamais écrite, complétée par la migration additive V36), nouvelle
 * fonctionnalité sans équivalent legacy (KB-GMAO-55). Les colonnes {@code date_creation} et {@code date_completion} de V5 ne
 * sont pas mappées : la première reçoit sa valeur par défaut, la seconde reste vide. {@code rang} garde l'ordre d'ajout ; {@code centreId} duplique celui de l'intervention
 * pour borner toute lecture au centre.
 */
@Entity
@Table(name = "gmao_taches_intervention")
public class TacheJpa {

    @Id
    UUID id;

    @Column(nullable = false)
    UUID interventionId;

    @Column(nullable = false)
    UUID centreId;

    @Column(nullable = false)
    int rang;

    @Column(nullable = false, length = 50)
    String statut;

    @Column(nullable = false, columnDefinition = "TEXT")
    String description;

    @Column(nullable = false, updatable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    Instant creeLe;

    @Column(updatable = false)
    UUID creePar;

    @Column(columnDefinition = "TIMESTAMP WITH TIME ZONE")
    Instant demarreLe;

    @Column
    UUID demarrePar;

    @Column(columnDefinition = "TIMESTAMP WITH TIME ZONE")
    Instant acheveLe;

    @Column
    UUID achevePar;

    protected TacheJpa() {
    }

    TacheJpa(UUID id) {
        this.id = id;
    }
}
