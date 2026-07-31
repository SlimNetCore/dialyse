package com.hemodialyse.backend.domain.seance.model;

import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;

public class RelevePerSeance {
    private UUID id;
    private UUID voletParamedicalId;
    private UUID centerId;
    private LocalTime heureReleve;
    private Integer taSystolique;
    private Integer taDiastolique;
    private Integer fc;
    private Integer pressionVeineuse;
    private Integer pressionArterielle;
    private OffsetDateTime createdAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getVoletParamedicalId() {
        return voletParamedicalId;
    }

    public void setVoletParamedicalId(UUID voletParamedicalId) {
        this.voletParamedicalId = voletParamedicalId;
    }

    public UUID getCenterId() {
        return centerId;
    }

    public void setCenterId(UUID centerId) {
        this.centerId = centerId;
    }

    public LocalTime getHeureReleve() {
        return heureReleve;
    }

    public void setHeureReleve(LocalTime heureReleve) {
        this.heureReleve = heureReleve;
    }

    public Integer getTaSystolique() {
        return taSystolique;
    }

    public void setTaSystolique(Integer taSystolique) {
        this.taSystolique = taSystolique;
    }

    public Integer getTaDiastolique() {
        return taDiastolique;
    }

    public void setTaDiastolique(Integer taDiastolique) {
        this.taDiastolique = taDiastolique;
    }

    public Integer getFc() {
        return fc;
    }

    public void setFc(Integer fc) {
        this.fc = fc;
    }

    public Integer getPressionVeineuse() {
        return pressionVeineuse;
    }

    public void setPressionVeineuse(Integer p) {
        this.pressionVeineuse = p;
    }

    public Integer getPressionArterielle() {
        return pressionArterielle;
    }

    public void setPressionArterielle(Integer p) {
        this.pressionArterielle = p;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
