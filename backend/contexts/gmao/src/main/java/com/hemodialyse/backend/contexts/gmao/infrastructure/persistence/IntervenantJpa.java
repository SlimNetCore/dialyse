package com.hemodialyse.backend.contexts.gmao.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Modèle de persistance d'un intervenant : table {@code gmao_intervenants}, la table historique, avec les mêmes noms de
 * colonnes que l'ancienne entité (ADR 0024) : la base arrondit le tarif à deux décimales (demi supérieur) et refuse
 * les dépassements (KB-GMAO-26/27), comme avant. Jamais exposé hors de {@code infrastructure.persistence}.
 */
@Entity
@Table(name = "gmao_intervenants")
public class IntervenantJpa {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID centreId;

    @Column(nullable = false, length = 255)
    private String nom;

    @Column(nullable = false, length = 20)
    private String type;

    @Column(length = 50)
    private String telephone;

    @Column(length = 255)
    private String email;

    @Column(precision = 10, scale = 2)
    private BigDecimal tarifHoraireDefaut;

    @Column(nullable = false)
    private boolean actif;

    protected IntervenantJpa() {
    }

    public IntervenantJpa(UUID id) {
        this.id = id;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCentreId() {
        return centreId;
    }

    public void setCentreId(UUID centreId) {
        this.centreId = centreId;
    }

    public String getNom() {
        return nom;
    }

    public void setNom(String nom) {
        this.nom = nom;
    }

    public String getNature() {
        return type;
    }

    public void setNature(String nature) {
        this.type = nature;
    }

    public String getTelephone() {
        return telephone;
    }

    public void setTelephone(String telephone) {
        this.telephone = telephone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public BigDecimal getTarifHoraire() {
        return tarifHoraireDefaut;
    }

    public void setTarifHoraire(BigDecimal tarifHoraire) {
        this.tarifHoraireDefaut = tarifHoraire;
    }

    public boolean isActif() {
        return actif;
    }

    public void setActif(boolean actif) {
        this.actif = actif;
    }
}
