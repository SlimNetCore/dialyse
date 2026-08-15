package com.hemodialyse.backend.infrastructure.persistence.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "mapping_comptable",
        uniqueConstraints = @UniqueConstraint(name = "uq_mapping_center", columnNames = "center_id"))
public class MappingComptableJpaEntity {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "center_id", nullable = false, columnDefinition = "uuid")
    private UUID centerId;

    @Column(name = "compte_ventes", length = 20, nullable = false)
    private String compteVentes;

    @Column(name = "compte_client_patient", length = 20, nullable = false)
    private String compteClientPatient;

    @Column(name = "compte_client_cnas", length = 20, nullable = false)
    private String compteClientCnas;

    @Column(name = "compte_client_casnos", length = 20, nullable = false)
    private String compteClientCasnos;

    @Column(name = "compte_client_mutuelle", length = 20, nullable = false)
    private String compteClientMutuelle;

    @Column(name = "compte_client_autre", length = 20, nullable = false)
    private String compteClientAutre;

    @Column(name = "compte_banque", length = 20, nullable = false)
    private String compteBanque;

    @Column(name = "compte_caisse", length = 20, nullable = false)
    private String compteCaisse;

    @Column(name = "compte_tva_collectee", length = 20)
    private String compteTVACollectee;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @Column(name = "updated_by", length = 120)
    private String updatedBy;

    @PrePersist
    @PreUpdate
    void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    // ─── Getters / Setters ───────────────────────────────────────────────────

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getCenterId() {
        return centerId;
    }

    public void setCenterId(UUID centerId) {
        this.centerId = centerId;
    }

    public String getCompteVentes() {
        return compteVentes;
    }

    public void setCompteVentes(String compteVentes) {
        this.compteVentes = compteVentes;
    }

    public String getCompteClientPatient() {
        return compteClientPatient;
    }

    public void setCompteClientPatient(String v) {
        this.compteClientPatient = v;
    }

    public String getCompteClientCnas() {
        return compteClientCnas;
    }

    public void setCompteClientCnas(String v) {
        this.compteClientCnas = v;
    }

    public String getCompteClientCasnos() {
        return compteClientCasnos;
    }

    public void setCompteClientCasnos(String v) {
        this.compteClientCasnos = v;
    }

    public String getCompteClientMutuelle() {
        return compteClientMutuelle;
    }

    public void setCompteClientMutuelle(String v) {
        this.compteClientMutuelle = v;
    }

    public String getCompteClientAutre() {
        return compteClientAutre;
    }

    public void setCompteClientAutre(String v) {
        this.compteClientAutre = v;
    }

    public String getCompteBanque() {
        return compteBanque;
    }

    public void setCompteBanque(String v) {
        this.compteBanque = v;
    }

    public String getCompteCaisse() {
        return compteCaisse;
    }

    public void setCompteCaisse(String v) {
        this.compteCaisse = v;
    }

    public String getCompteTVACollectee() {
        return compteTVACollectee;
    }

    public void setCompteTVACollectee(String v) {
        this.compteTVACollectee = v;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }
}

