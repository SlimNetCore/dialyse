package com.hemodialyse.backend.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Société chapeautant un ou plusieurs centres. Les centres sont rattachés via {@code centers.societe_id}
 * (mapping « à plat » : pas de relation JPA, cohérent avec le reste du projet).
 */
@Entity
@Table(name = "societes")
public class SocieteJpaEntity {

    @Id
    private UUID id;

    @Column(name = "code", nullable = false, length = 30, unique = true)
    private String code;

    @Column(name = "raison_sociale", nullable = false, length = 200)
    private String raisonSociale;

    @Column(name = "nif", length = 40)
    private String nif;

    @Column(name = "nis", length = 40)
    private String nis;

    @Column(name = "rc", length = 40)
    private String rc;

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

    @Column(name = "pied_page", length = 500)
    private String piedPage;

    /**
     * Logo (PNG ou JPEG, validé à l'envoi) imprimé dans l'en-tête des documents.
     */
    @Column(name = "logo", columnDefinition = "BYTEA")
    private byte[] logo;

    @Column(name = "logo_content_type", length = 30)
    private String logoContentType;

    @Column(name = "actif", nullable = false)
    private boolean actif;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

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

    public String getRaisonSociale() {
        return raisonSociale;
    }

    public void setRaisonSociale(String raisonSociale) {
        this.raisonSociale = raisonSociale;
    }

    public String getNif() {
        return nif;
    }

    public void setNif(String nif) {
        this.nif = nif;
    }

    public String getNis() {
        return nis;
    }

    public void setNis(String nis) {
        this.nis = nis;
    }

    public String getRc() {
        return rc;
    }

    public void setRc(String rc) {
        this.rc = rc;
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

    public String getPiedPage() {
        return piedPage;
    }

    public void setPiedPage(String piedPage) {
        this.piedPage = piedPage;
    }

    public byte[] getLogo() {
        return logo;
    }

    public void setLogo(byte[] logo) {
        this.logo = logo;
    }

    public String getLogoContentType() {
        return logoContentType;
    }

    public void setLogoContentType(String logoContentType) {
        this.logoContentType = logoContentType;
    }

    public boolean isActif() {
        return actif;
    }

    public void setActif(boolean actif) {
        this.actif = actif;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
