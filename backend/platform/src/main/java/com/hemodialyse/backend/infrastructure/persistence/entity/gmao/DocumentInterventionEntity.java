package com.hemodialyse.backend.infrastructure.persistence.entity.gmao;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Pièce jointe d'intervention GMAO (contenu binaire en base, borné à 5 Mo par le domaine).
 */
@Entity
@Table(name = "gmao_documents_intervention")
public class DocumentInterventionEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID interventionId;

    @Column(nullable = false)
    private UUID centreId;

    @Column(nullable = false, length = 30)
    private String type;

    @Column(nullable = false, length = 150)
    private String nom;

    @Column(nullable = false, length = 50)
    private String contentType;

    @Column(nullable = false)
    private long taille;

    @Column(name = "contenu", nullable = false, columnDefinition = "BYTEA")
    private byte[] contenu;

    @Column
    private UUID ajoutePar;

    @Column(nullable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime ajouteLe;

    public DocumentInterventionEntity() {
    }

    public DocumentInterventionEntity(UUID id, UUID interventionId, UUID centreId, String type, String nom,
                                      String contentType, long taille, byte[] contenu, UUID ajoutePar,
                                      OffsetDateTime ajouteLe) {
        this.id = id;
        this.interventionId = interventionId;
        this.centreId = centreId;
        this.type = type;
        this.nom = nom;
        this.contentType = contentType;
        this.taille = taille;
        this.contenu = contenu;
        this.ajoutePar = ajoutePar;
        this.ajouteLe = ajouteLe;
    }

    public UUID getId() {
        return id;
    }

    public UUID getInterventionId() {
        return interventionId;
    }

    public UUID getCentreId() {
        return centreId;
    }

    public String getType() {
        return type;
    }

    public String getNom() {
        return nom;
    }

    public String getContentType() {
        return contentType;
    }

    public long getTaille() {
        return taille;
    }

    public byte[] getContenu() {
        return contenu;
    }

    public UUID getAjoutePar() {
        return ajoutePar;
    }

    public OffsetDateTime getAjouteLe() {
        return ajouteLe;
    }
}
