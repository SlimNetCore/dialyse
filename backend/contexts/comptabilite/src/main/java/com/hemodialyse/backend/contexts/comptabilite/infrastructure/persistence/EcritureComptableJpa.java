package com.hemodialyse.backend.contexts.comptabilite.infrastructure.persistence;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Description JPA de la table {@code ecritures_comptables}, reprise telle quelle de la plateforme au retrait de
 * {@code bc-comptabilite} (slice C7). Elle ne sert qu'au schéma créé par {@code ddl-auto} (tests H2) ; le contexte lit et
 * écrit les écritures par JDBC ({@link EcritureComptableRepositoryJdbc}). En production, le schéma vient de Flyway.
 */
@Entity
@Table(name = "ecritures_comptables",
        indexes = {
                @Index(name = "idx_ecriture_center_period", columnList = "center_id, journal_code, date_ecriture"),
                @Index(name = "idx_ecriture_center_source", columnList = "center_id, source_id")})
public class EcritureComptableJpa {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "center_id", nullable = false, columnDefinition = "uuid")
    private UUID centerId;

    @Column(name = "journal_code", nullable = false, length = 10)
    private String journalCode;

    @Column(name = "date_ecriture", nullable = false)
    private LocalDate dateEcriture;

    @Column(name = "date_piece", nullable = false)
    private LocalDate datePiece;

    @Column(name = "numero_piece", nullable = false, length = 30)
    private String numeroPiece;

    @Column(name = "libelle", length = 255)
    private String libelle;

    @Column(name = "statut", nullable = false, length = 20)
    private String statut;

    @Column(name = "source_id", columnDefinition = "uuid")
    private UUID sourceId;

    @Column(name = "modele_id", columnDefinition = "uuid")
    private UUID modeleId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @OneToMany(mappedBy = "ecriture", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("id ASC")
    private List<LigneEcritureJpa> lignes = new ArrayList<>();

    protected EcritureComptableJpa() {
    }
}

