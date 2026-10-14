package com.hemodialyse.backend.contexts.gmao.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Modèle de persistance d'une intervention : table {@code gmao_interventions}, la table historique, avec les mêmes noms de
 * colonnes, longueurs et nullités que l'ancienne entité (ADR 0024) : la base refuse les valeurs trop longues, comme avant.
 * Champs visibles du seul paquet : ce modèle n'a pas d'autre logique que le mapping, il n'est jamais exposé hors de
 * {@code infrastructure.persistence}.
 */
@Entity
@Table(name = "gmao_interventions")
public class InterventionJpa {

    @Id
    UUID id;

    @Column(nullable = false)
    UUID equipementId;

    @Column(nullable = false)
    UUID centreId;

    @Column(nullable = false, length = 50)
    String type;

    @Column(nullable = false, length = 50)
    String statut;

    @Column(nullable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    Instant dateDebut;

    @Column(columnDefinition = "TIMESTAMP WITH TIME ZONE")
    Instant dateFin;

    @Column
    UUID intervenantId;

    @Column(length = 50)
    String etatEquipementAvant;

    @Column(length = 50)
    String etatEquipementApres;

    @Column(length = 20)
    String priorite;

    @Column(columnDefinition = "TIMESTAMP WITH TIME ZONE")
    Instant echeance;

    @Column(columnDefinition = "TEXT")
    String symptome;

    @Column(columnDefinition = "TEXT")
    String cause;

    @Column
    UUID demarrePar;

    @Column(columnDefinition = "TIMESTAMP WITH TIME ZONE")
    Instant demarreLe;

    @Column
    UUID cloturePar;

    @Column(columnDefinition = "TIMESTAMP WITH TIME ZONE")
    Instant clotureLe;

    @Column
    UUID annulePar;

    @Column(columnDefinition = "TIMESTAMP WITH TIME ZONE")
    Instant annuleLe;

    @Column(nullable = false, columnDefinition = "TEXT")
    String description;

    @Column(columnDefinition = "TEXT")
    String actions;

    @Column(length = 255)
    String pieceRemplacee;

    @Column(columnDefinition = "TEXT")
    String observations;

    @Column(nullable = false, updatable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    Instant dateCreation;

    @Column(columnDefinition = "TIMESTAMP WITH TIME ZONE")
    Instant dateModification;

    @Column(nullable = false, updatable = false)
    UUID creePar;

    @Column
    UUID modifiePar;

    @Column(columnDefinition = "TIMESTAMP WITH TIME ZONE")
    Instant deletedAt;

    protected InterventionJpa() {
    }

    InterventionJpa(UUID id) {
        this.id = id;
    }
}
