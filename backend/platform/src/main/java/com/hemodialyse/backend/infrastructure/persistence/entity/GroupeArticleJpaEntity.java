package com.hemodialyse.backend.infrastructure.persistence.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Groupe nommé d'articles d'un centre (ex. « KIT CNAS »). Nom unique par centre via {@code nom_cle}.
 */
@Entity
@Table(name = "groupes_articles",
        uniqueConstraints = @UniqueConstraint(name = "uk_groupes_articles_centre_nom", columnNames = {"center_id", "nom_cle"}))
public class GroupeArticleJpaEntity {

    @Id
    private UUID id;

    @Column(name = "center_id", nullable = false)
    private UUID centerId;

    @Column(name = "nom", nullable = false, length = 100)
    private String nom;

    /**
     * Nom normalisé (minuscules, sans accents) : clé d'unicité et de consolidation.
     */
    @Column(name = "nom_cle", nullable = false, length = 100)
    private String nomCle;

    @Column(name = "description", length = 500)
    private String description;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "groupes_articles_items", joinColumns = @JoinColumn(name = "groupe_id"))
    @Column(name = "article_id", nullable = false)
    private Set<UUID> articleIds = new HashSet<>();

    @Column(name = "updated_at", nullable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime updatedAt;

    public GroupeArticleJpaEntity() {
    }

    public GroupeArticleJpaEntity(UUID id, UUID centerId, String nom, String nomCle, String description,
                                  Set<UUID> articleIds, OffsetDateTime updatedAt) {
        this.id = id;
        this.centerId = centerId;
        this.nom = nom;
        this.nomCle = nomCle;
        this.description = description;
        this.articleIds = new HashSet<>(articleIds);
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCenterId() {
        return centerId;
    }

    public String getNom() {
        return nom;
    }

    public String getNomCle() {
        return nomCle;
    }

    public String getDescription() {
        return description;
    }

    public Set<UUID> getArticleIds() {
        return articleIds;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
