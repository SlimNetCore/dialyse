package com.hemodialyse.backend.contexts.comptabilite.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.UUID;

/**
 * Modèle de persistance d'un compte du plan comptable : table {@code comptes_comptables}, la table historique (V33), avec
 * les mêmes noms de colonnes, longueurs et contrainte d'unicité. Jamais exposé hors de {@code infrastructure.persistence}.
 */
@Entity
@Table(name = "comptes_comptables",
        uniqueConstraints = @UniqueConstraint(name = "uq_compte_comptable_centre_numero", columnNames = {"center_id", "numero"}))
public class CompteComptableJpa {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "center_id", nullable = false, columnDefinition = "uuid")
    private UUID centerId;

    @Column(name = "numero", nullable = false, length = 20)
    private String numero;

    @Column(name = "libelle", nullable = false, length = 150)
    private String libelle;

    @Column(name = "actif", nullable = false)
    private boolean actif;

    protected CompteComptableJpa() {
    }

    CompteComptableJpa(UUID id, UUID centerId, String numero) {
        this.id = id;
        this.centerId = centerId;
        this.numero = numero;
    }

    UUID getId() {
        return id;
    }

    UUID getCenterId() {
        return centerId;
    }

    String getNumero() {
        return numero;
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
