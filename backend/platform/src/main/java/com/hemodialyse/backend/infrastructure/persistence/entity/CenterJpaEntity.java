package com.hemodialyse.backend.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * Centre d'hémodialyse. Le rattachement à une société ({@code societe_id}) est obligatoire : sur un schéma généré
 * la colonne est {@code NOT NULL} ; sur une base existante, la migration V33 rattache d'abord les centres à une
 * « Société par défaut » puis pose la contrainte. Les coordonnées restent facultatives.
 */
@Entity
@Table(name = "centers")
public class CenterJpaEntity {

    @Id
    private UUID id;

    @Column(name = "code")
    private String code;

    @Column(name = "name")
    private String name;

    @Column(name = "societe_id", nullable = false)
    private UUID societeId;

    @Column(name = "adresse", length = 250)
    private String adresse;

    @Column(name = "ville", length = 100)
    private String ville;

    @Column(name = "wilaya", length = 100)
    private String wilaya;

    @Column(name = "telephone", length = 30)
    private String telephone;

    @Column(name = "email", length = 150)
    private String email;

    @Column(name = "site_web", length = 200)
    private String siteWeb;

    /**
     * {@code null} (lignes antérieures à l'introduction des sociétés) est interprété comme actif.
     */
    @Column(name = "actif")
    private Boolean actif;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public UUID getSocieteId() {
        return societeId;
    }

    public void setSocieteId(UUID societeId) {
        this.societeId = societeId;
    }

    public String getAdresse() {
        return adresse;
    }

    public void setAdresse(String adresse) {
        this.adresse = adresse;
    }

    public String getVille() {
        return ville;
    }

    public void setVille(String ville) {
        this.ville = ville;
    }

    public String getWilaya() {
        return wilaya;
    }

    public void setWilaya(String wilaya) {
        this.wilaya = wilaya;
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

    public String getSiteWeb() {
        return siteWeb;
    }

    public void setSiteWeb(String siteWeb) {
        this.siteWeb = siteWeb;
    }

    public boolean isActif() {
        return actif == null || actif;
    }

    public void setActif(boolean actif) {
        this.actif = actif;
    }
}
