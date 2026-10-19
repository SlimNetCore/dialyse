package com.hemodialyse.backend.contexts.comptabilite.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Description JPA de la table {@code lignes_ecriture} (schéma seulement, voir {@link EcritureComptableJpa}).
 */
@Entity
@Table(name = "lignes_ecriture", indexes = {
        @Index(name = "idx_lignes_ecriture_ecriture", columnList = "ecriture_id"),
        @Index(name = "idx_lignes_ecriture_compte", columnList = "compte_scf")})
public class LigneEcritureJpa {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ecriture_id", nullable = false)
    private EcritureComptableJpa ecriture;

    @Column(name = "compte_scf", nullable = false, length = 20)
    private String compteSCF;

    @Column(name = "libelle_ligne", length = 255)
    private String libelleLigne;

    @Column(name = "montant_debit", precision = 14, scale = 2)
    private BigDecimal montantDebit;

    @Column(name = "montant_credit", precision = 14, scale = 2)
    private BigDecimal montantCredit;

    @Column(name = "tiers_id", columnDefinition = "uuid")
    private UUID tiersId;

    @Column(name = "axes_analytiques", length = 500)
    private String axesAnalytiques;

    protected LigneEcritureJpa() {
    }
}

