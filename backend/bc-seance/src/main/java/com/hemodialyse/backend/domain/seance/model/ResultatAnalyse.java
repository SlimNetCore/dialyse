package com.hemodialyse.backend.domain.seance.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public class ResultatAnalyse {
    private UUID id;
    private UUID patientId;
    private UUID centerId;
    private LocalDate datePrelevement;
    private BigDecimal hbGDl;
    private BigDecimal htPct;
    private Integer plaquettes;
    private BigDecimal ferritineNgMl;
    private BigDecimal cstfPct;
    private BigDecimal epoEndogeneMuiMl;
    private BigDecimal ureePreMgDl;
    private BigDecimal ureePostMgDl;
    private BigDecimal creatinineMgDl;
    private BigDecimal ktVMensuel;
    private BigDecimal phosphoreMgDl;
    private BigDecimal calciumMgDl;
    private BigDecimal pthPgMl;
    private BigDecimal albumineGDl;
    private BigDecimal proteinesGDl;
    private BigDecimal crpMgL;
    private OffsetDateTime createdAt;
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

