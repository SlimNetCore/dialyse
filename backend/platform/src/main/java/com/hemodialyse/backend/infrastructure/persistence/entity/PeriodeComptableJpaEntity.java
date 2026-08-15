package com.hemodialyse.backend.infrastructure.persistence.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "periodes_comptables",
        uniqueConstraints = @UniqueConstraint(name = "uq_periode_center", columnNames = {"center_id", "annee", "mois"}))
public class PeriodeComptableJpaEntity {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "center_id", nullable = false, columnDefinition = "uuid")
    private UUID centerId;

    @Column(name = "annee", nullable = false)
    private int annee;

    @Column(name = "mois", nullable = false)
    private int mois;

    @Column(name = "cloturee", nullable = false)
    private boolean cloturee;

    @Column(name = "cloturee_at")
    private OffsetDateTime clotureeAt;

    @Column(name = "cloturee_by", length = 120)
    private String clotureeBy;

    // ─── Getters / Setters ───────────────────────────────────────────────────

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

    public int getAnnee() {
        return annee;
    }

    public void setAnnee(int annee) {
        this.annee = annee;
    }

    public int getMois() {
        return mois;
    }

    public void setMois(int mois) {
        this.mois = mois;
    }

    public boolean isCloturee() {
        return cloturee;
    }

    public void setCloturee(boolean cloturee) {
        this.cloturee = cloturee;
    }

    public OffsetDateTime getClotureeAt() {
        return clotureeAt;
    }

    public void setClotureeAt(OffsetDateTime clotureeAt) {
        this.clotureeAt = clotureeAt;
    }

    public String getClotureeBy() {
        return clotureeBy;
    }

    public void setClotureeBy(String clotureeBy) {
        this.clotureeBy = clotureeBy;
    }
}

