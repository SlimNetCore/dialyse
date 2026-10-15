package com.hemodialyse.backend.contexts.comptabilite.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.UUID;

/**
 * Modèle de persistance d'un journal comptable : table {@code journaux_comptables}, la table historique (V32), avec les
 * mêmes noms de colonnes, longueurs et contrainte d'unicité. Jamais exposé hors de {@code infrastructure.persistence}.
 */
@Entity
@Table(name = "journaux_comptables",
        uniqueConstraints = @UniqueConstraint(name = "uq_journal_comptable_centre_code", columnNames = {"center_id", "code"}))
public class JournalComptableJpa {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "center_id", nullable = false, columnDefinition = "uuid")
    private UUID centerId;

    @Column(name = "code", nullable = false, length = 10)
    private String code;

    @Column(name = "libelle", nullable = false, length = 100)
    private String libelle;

    @Column(name = "actif", nullable = false)
    private boolean actif;

    protected JournalComptableJpa() {
    }

    JournalComptableJpa(UUID id, UUID centerId, String code) {
        this.id = id;
        this.centerId = centerId;
        this.code = code;
    }

    UUID getId() {
        return id;
    }

    UUID getCenterId() {
        return centerId;
    }

    String getCode() {
        return code;
    }

    String getLibelle() {
        return libelle;
    }

    void setLibelle(String libelle) {
        this.libelle = libelle;
    }

    boolean isActif() {
        return actif;
    }

    void setActif(boolean actif) {
        this.actif = actif;
    }
}
