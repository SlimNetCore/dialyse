package com.hemodialyse.backend.contexts.gmao.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Modèle de persistance d'une pièce jointe : table {@code gmao_documents_intervention}, la table historique, avec les mêmes
 * noms de colonnes, longueurs et nullités que l'ancienne entité (ADR 0024). Les octets restent en base ({@code BYTEA}), dans
 * la colonne {@code contenu}. Champs visibles du seul paquet : ce modèle n'a pas d'autre logique que le mapping.
 */
@Entity
@Table(name = "gmao_documents_intervention")
public class PieceJointeJpa {

    @Id
    UUID id;

    @Column(nullable = false)
    UUID interventionId;

    @Column(nullable = false)
    UUID centreId;

    @Column(nullable = false, length = 30)
    String type;

    @Column(nullable = false, length = 150)
    String nom;

    @Column(nullable = false, length = 50)
    String contentType;

    @Column(nullable = false)
    long taille;

    @Column(name = "contenu", nullable = false, columnDefinition = "BYTEA")
    byte[] contenu;

    @Column
    UUID ajoutePar;

    @Column(nullable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    Instant ajouteLe;

    protected PieceJointeJpa() {
    }

    PieceJointeJpa(UUID id) {
        this.id = id;
    }
}
