package com.hemodialyse.backend.infrastructure.persistence.entity.gmao;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Entité JPA pour le référentiel Intervenant GMAO (technicien interne / prestataire externe).
 */
@Entity
@Table(name = "gmao_intervenants")
public class IntervenantEntity {

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

    public IntervenantEntity() {
    }

    public IntervenantEntity(UUID id, UUID centreId, String nom, String type, String telephone,
                             String email, BigDecimal tarifHoraireDefaut, boolean actif) {
        this.id = id;
        this.centreId = centreId;
        this.nom = nom;
        this.type = type;
        this.telephone = telephone;
        this.email = email;
        this.tarifHoraireDefaut = tarifHoraireDefaut;
        this.actif = actif;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
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

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
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

    public BigDecimal getTarifHoraireDefaut() {
        return tarifHoraireDefaut;
    }

    public void setTarifHoraireDefaut(BigDecimal tarifHoraireDefaut) {
        this.tarifHoraireDefaut = tarifHoraireDefaut;
    }

    public boolean isActif() {
        return actif;
    }

    public void setActif(boolean actif) {
        this.actif = actif;
    }
}
