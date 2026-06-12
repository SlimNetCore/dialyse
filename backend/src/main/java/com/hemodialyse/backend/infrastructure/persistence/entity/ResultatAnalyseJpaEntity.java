package com.hemodialyse.backend.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "resultats_analyses")
public class ResultatAnalyseJpaEntity {
    @Id
    private UUID id;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "center_id", nullable = false)
    private UUID centerId;

    @Column(name = "date_prelevement", nullable = false)
    private LocalDate datePrelevement;

    @Column(name = "hb_g_dl")
    private BigDecimal hbGDl;
    @Column(name = "ht_pct")
    private BigDecimal htPct;
    @Column(name = "plaquettes")
    private Integer plaquettes;

    @Column(name = "ferritine_ng_ml")
    private BigDecimal ferritineNgMl;
    @Column(name = "cstf_pct")
    private BigDecimal cstfPct;
    @Column(name = "epo_endogene_mui_ml")
    private BigDecimal epoEndogeneMuiMl;

    @Column(name = "uree_pre_mg_dl")
    private BigDecimal ureePreMgDl;
    @Column(name = "uree_post_mg_dl")
    private BigDecimal ureePostMgDl;
    @Column(name = "creatinine_mg_dl")
    private BigDecimal creatinineMgDl;
    @Column(name = "kt_v_mensuel")
    private BigDecimal ktVMensuel;

    @Column(name = "phosphore_mg_dl")
    private BigDecimal phosphoreMgDl;
    @Column(name = "calcium_mg_dl")
    private BigDecimal calciumMgDl;
    @Column(name = "pth_pg_ml")
    private BigDecimal pthPgMl;

    @Column(name = "albumine_g_dl")
    private BigDecimal albumineGDl;
    @Column(name = "proteines_g_dl")
    private BigDecimal proteinesGDl;
    @Column(name = "crp_mg_l")
    private BigDecimal crpMgL;

    @Column(name = "created_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime updatedAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getPatientId() {
        return patientId;
    }

    public void setPatientId(UUID patientId) {
        this.patientId = patientId;
    }

    public UUID getCenterId() {
        return centerId;
    }

    public void setCenterId(UUID centerId) {
        this.centerId = centerId;
    }

    public LocalDate getDatePrelevement() {
        return datePrelevement;
    }

    public void setDatePrelevement(LocalDate datePrelevement) {
        this.datePrelevement = datePrelevement;
    }

    public BigDecimal getHbGDl() {
        return hbGDl;
    }

    public void setHbGDl(BigDecimal hbGDl) {
        this.hbGDl = hbGDl;
    }

    public BigDecimal getHtPct() {
        return htPct;
    }

    public void setHtPct(BigDecimal htPct) {
        this.htPct = htPct;
    }

    public Integer getPlaquettes() {
        return plaquettes;
    }

    public void setPlaquettes(Integer plaquettes) {
        this.plaquettes = plaquettes;
    }

    public BigDecimal getFerritineNgMl() {
        return ferritineNgMl;
    }

    public void setFerritineNgMl(BigDecimal ferritineNgMl) {
        this.ferritineNgMl = ferritineNgMl;
    }

    public BigDecimal getCstfPct() {
        return cstfPct;
    }

    public void setCstfPct(BigDecimal cstfPct) {
        this.cstfPct = cstfPct;
    }

    public BigDecimal getEpoEndogeneMuiMl() {
        return epoEndogeneMuiMl;
    }

    public void setEpoEndogeneMuiMl(BigDecimal epoEndogeneMuiMl) {
        this.epoEndogeneMuiMl = epoEndogeneMuiMl;
    }

    public BigDecimal getUreePreMgDl() {
        return ureePreMgDl;
    }

    public void setUreePreMgDl(BigDecimal ureePreMgDl) {
        this.ureePreMgDl = ureePreMgDl;
    }

    public BigDecimal getUreePostMgDl() {
        return ureePostMgDl;
    }

    public void setUreePostMgDl(BigDecimal ureePostMgDl) {
        this.ureePostMgDl = ureePostMgDl;
    }

    public BigDecimal getCreatinineMgDl() {
        return creatinineMgDl;
    }

    public void setCreatinineMgDl(BigDecimal creatinineMgDl) {
        this.creatinineMgDl = creatinineMgDl;
    }

    public BigDecimal getKtVMensuel() {
        return ktVMensuel;
    }

    public void setKtVMensuel(BigDecimal ktVMensuel) {
        this.ktVMensuel = ktVMensuel;
    }

    public BigDecimal getPhosphoreMgDl() {
        return phosphoreMgDl;
    }

    public void setPhosphoreMgDl(BigDecimal phosphoreMgDl) {
        this.phosphoreMgDl = phosphoreMgDl;
    }

    public BigDecimal getCalciumMgDl() {
        return calciumMgDl;
    }

    public void setCalciumMgDl(BigDecimal calciumMgDl) {
        this.calciumMgDl = calciumMgDl;
    }

    public BigDecimal getPthPgMl() {
        return pthPgMl;
    }

    public void setPthPgMl(BigDecimal pthPgMl) {
        this.pthPgMl = pthPgMl;
    }

    public BigDecimal getAlbumineGDl() {
        return albumineGDl;
    }

    public void setAlbumineGDl(BigDecimal albumineGDl) {
        this.albumineGDl = albumineGDl;
    }

    public BigDecimal getProteinesGDl() {
        return proteinesGDl;
    }

    public void setProteinesGDl(BigDecimal proteinesGDl) {
        this.proteinesGDl = proteinesGDl;
    }

    public BigDecimal getCrpMgL() {
        return crpMgL;
    }

    public void setCrpMgL(BigDecimal crpMgL) {
        this.crpMgL = crpMgL;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}

