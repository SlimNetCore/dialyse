package com.hemodialyse.backend.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.UUID;

/**
 * Journal comptable paramétré par un centre.
 */
@Entity
@Table(name = "journaux_comptables",
        uniqueConstraints = @UniqueConstraint(name = "uq_journal_comptable_centre_code", columnNames = {"center_id", "code"}))
public class JournalComptableJpaEntity {

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

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getCenterId() {
        return centerId;
    }

    public void setCenterId(UUID centerId) {
        this.centerId = centerId;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getLibelle() {
        return libelle;
    }

    public void setLibelle(String libelle) {
        this.libelle = libelle;
    }

    public boolean isActif() {
        return actif;
    }

    public void setActif(boolean actif) {
        this.actif = actif;
    }
}
