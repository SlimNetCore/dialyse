package com.hemodialyse.backend.infrastructure.persistence.entity;

import jakarta.persistence.*;

import java.util.UUID;

/**
 * Auto-incremented prefixed sequence counters (BL/BR/BS) per center.
 * Declared as a JPA entity so the table is created by Hibernate ddl-auto in all
 * environments (in production Flyway V16 also creates/seeds it; update mode reconciles).
 * Read/written transactionally by {@code StockSequenceAdapter} via JdbcTemplate.
 */
@Entity
@Table(name = "app_settings")
@IdClass(AppSettingId.class)
public class AppSettingJpaEntity {

    @Id
    @Column(name = "center_id", nullable = false)
    private UUID centerId;

    @Id
    @Column(name = "cle", nullable = false, length = 50)
    private String cle;

    @Column(name = "prefixe", nullable = false, length = 20)
    private String prefixe;

    @Column(name = "dernier_compteur", nullable = false)
    private long dernierCompteur;

    public UUID getCenterId() {
        return centerId;
    }

    public void setCenterId(UUID centerId) {
        this.centerId = centerId;
    }

    public String getCle() {
        return cle;
    }

    public void setCle(String cle) {
        this.cle = cle;
    }

    public String getPrefixe() {
        return prefixe;
    }

    public void setPrefixe(String prefixe) {
        this.prefixe = prefixe;
    }

    public long getDernierCompteur() {
        return dernierCompteur;
    }

    public void setDernierCompteur(long dernierCompteur) {
        this.dernierCompteur = dernierCompteur;
    }
}

