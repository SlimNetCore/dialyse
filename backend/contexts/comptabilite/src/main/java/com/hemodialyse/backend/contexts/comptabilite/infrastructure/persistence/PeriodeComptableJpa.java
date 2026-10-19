package com.hemodialyse.backend.contexts.comptabilite.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Description JPA de la table {@code periodes_comptables} (schéma seulement, voir {@link EcritureComptableJpa}) ; le
 * contexte lit et écrit les périodes par JDBC ({@link PeriodeComptableRepositoryJdbc}).
 */
@Entity
@Table(name = "periodes_comptables",
        uniqueConstraints = @UniqueConstraint(name = "uq_periode_center", columnNames = {"center_id", "annee", "mois"}))
public class PeriodeComptableJpa {

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

    protected PeriodeComptableJpa() {
    }
}

