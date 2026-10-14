package com.hemodialyse.backend.contexts.gmao.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Modèle de persistance d'une ligne de coût : table {@code gmao_lignes_cout_intervention}, mêmes colonnes que l'ancienne
 * entité. La base arrondit la quantité à trois décimales et le prix à deux (demi supérieur) et refuse les dépassements
 * (KB-GMAO-44/45). {@code automatique} reste nullable : une ligne ancienne, sans valeur, est une ligne saisie.
 */
@Entity
@Table(name = "gmao_lignes_cout_intervention")
public class LigneDeCoutJpa {

    @Id
    UUID id;

    @Column(nullable = false)
    UUID interventionId;

    @Column(nullable = false, length = 30)
    String type;

    @Column(nullable = false, length = 255)
    String libelle;

    @Column(nullable = false, precision = 12, scale = 3)
    BigDecimal quantite;

    @Column(nullable = false, precision = 12, scale = 2)
    BigDecimal prixUnitaire;

    @Column
    UUID articleStockId;

    @Column
    Boolean automatique;

    protected LigneDeCoutJpa() {
    }

    LigneDeCoutJpa(UUID id) {
        this.id = id;
    }
}
