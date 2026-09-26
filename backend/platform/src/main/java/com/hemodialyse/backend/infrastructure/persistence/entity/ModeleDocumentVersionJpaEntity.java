package com.hemodialyse.backend.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Version personnalisée (téléversée puis validée) d'un modèle d'impression d'un centre.
 * Les versions sont immuables ; une seule est active par modèle. Sans version active, c'est le
 * modèle d'origine livré avec l'application qui est utilisé.
 */
@Entity
@Table(name = "modele_document_version",
        uniqueConstraints = @UniqueConstraint(name = "uk_modele_version", columnNames = {"modele_id", "version"}),
        indexes = @Index(name = "idx_modele_version_modele", columnList = "modele_id, center_id"))
public class ModeleDocumentVersionJpaEntity {

    @Id
    private UUID id;

    @Column(name = "modele_id", nullable = false)
    private UUID modeleId;

    @Column(name = "center_id", nullable = false)
    private UUID centerId;

    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "contenu", nullable = false, columnDefinition = "TEXT")
    private String contenu;

    @Column(name = "sha256", nullable = false, length = 64)
    private String sha256;

    @Column(name = "taille_octets", nullable = false)
    private int tailleOctets;

    @Column(name = "commentaire", length = 500)
    private String commentaire;

    @Column(name = "uploaded_by", nullable = false, length = 100)
    private String uploadedBy;

    @Column(name = "uploaded_at", nullable = false)
    private OffsetDateTime uploadedAt;

    @Column(name = "actif", nullable = false)
    private boolean actif;

    protected ModeleDocumentVersionJpaEntity() {
    }

    public ModeleDocumentVersionJpaEntity(UUID id, UUID modeleId, UUID centerId, int version, String contenu,
                                          String sha256, int tailleOctets, String commentaire, String uploadedBy,
                                          OffsetDateTime uploadedAt, boolean actif) {
        this.id = id;
        this.modeleId = modeleId;
        this.centerId = centerId;
        this.version = version;
        this.contenu = contenu;
        this.sha256 = sha256;
        this.tailleOctets = tailleOctets;
        this.commentaire = commentaire;
        this.uploadedBy = uploadedBy;
        this.uploadedAt = uploadedAt;
        this.actif = actif;
    }

    public UUID getId() {
        return id;
    }

    public UUID getModeleId() {
        return modeleId;
    }

    public UUID getCenterId() {
        return centerId;
    }

    public int getVersion() {
        return version;
    }

    public String getContenu() {
        return contenu;
    }

    public String getSha256() {
        return sha256;
    }

    public int getTailleOctets() {
        return tailleOctets;
    }

    public String getCommentaire() {
        return commentaire;
    }

    public String getUploadedBy() {
        return uploadedBy;
    }

    public OffsetDateTime getUploadedAt() {
        return uploadedAt;
    }

    public boolean isActif() {
        return actif;
    }

    public void setActif(boolean actif) {
        this.actif = actif;
    }
}
