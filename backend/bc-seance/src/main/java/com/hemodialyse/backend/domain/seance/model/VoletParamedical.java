package com.hemodialyse.backend.domain.seance.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public class VoletParamedical {
    private UUID id;
    private UUID seanceId;
    private UUID centerId;

    // Pré-séance
    private BigDecimal poidsSecCibleKg;
    private BigDecimal poidsAvantKg;
    private BigDecimal surchargeHydriqueKg;
    private BigDecimal poidsApresKg;
    private String taAvant;
    private String taApres;
    private Integer taSystoliqueAvant;
    private Integer taDiastoliqueAvant;
    private Integer fcAvant;
    private BigDecimal temperatureAvant;
    private Integer etatGeneralScore;
    private Boolean oedemes;
    private String oedemesLocalisation;
    private Boolean dyspnee;

    // Paramètres machine
    private Integer qbMlMin;
    private Integer qdMlMin;
    private Integer ufCibleMl;
    private Integer dureePreviMin;
    private Integer dureeMinutes;
    private Integer debitSangMlMin;
    private BigDecimal ultrafiltrationMl;
    private String typeDialyseur;
    private String typeBain;
    private String typeDialysat;
    private BigDecimal conductivite;
    private BigDecimal temperatureBain;

    // Anticoagulation
    private String anticoagulant;
    private String anticoagType;
    private BigDecimal anticoagDoseInitiale;
    private BigDecimal anticoagDoseHoraire;
    private Integer nbRincages;
    private Integer volumeRincageMl;
    private String heureArretHeparine;

    // Abord vasculaire
    private String abordType;
    private String abordCote;
    private String aiguilleCalibr;
    private String ordrePonction;
    private String aspectSite;
    private Boolean incidentPonction;
    private String incidentPonctionDetail;

    // Post-séance
    private Integer taSystoliqueApres;
    private Integer taDiastoliqueApres;
    private Integer fcApres;
    private BigDecimal ktVRealise;
    private Integer ufReelleMl;

    // Incidents
    private String incidents;
    private Boolean incidentHypotension;
    private Boolean incidentCrampes;
    private Boolean incidentCephalees;
    private Boolean incidentFrissons;
    private Boolean incidentNausees;
    private Boolean incidentThrombose;
    private String incidentAutre;

    // Signature
    private String signatureInfirmierId;
    private OffsetDateTime signatureAt;

    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    // ── Getters / Setters ──────────────────────────────────────────

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getSeanceId() {
        return seanceId;
    }

    public void setSeanceId(UUID seanceId) {
        this.seanceId = seanceId;
    }

    public UUID getCenterId() {
        return centerId;
    }

    public void setCenterId(UUID centerId) {
        this.centerId = centerId;
    }

    public BigDecimal getPoidsSecCibleKg() {
        return poidsSecCibleKg;
    }

    public void setPoidsSecCibleKg(BigDecimal v) {
        this.poidsSecCibleKg = v;
    }

    public BigDecimal getPoidsAvantKg() {
        return poidsAvantKg;
    }

    public void setPoidsAvantKg(BigDecimal v) {
        this.poidsAvantKg = v;
    }

    public BigDecimal getSurchargeHydriqueKg() {
        return surchargeHydriqueKg;
    }

    public void setSurchargeHydriqueKg(BigDecimal v) {
        this.surchargeHydriqueKg = v;
    }

    public BigDecimal getPoidsApresKg() {
        return poidsApresKg;
    }

    public void setPoidsApresKg(BigDecimal v) {
        this.poidsApresKg = v;
    }

    public String getTaAvant() {
        return taAvant;
    }

    public void setTaAvant(String v) {
        this.taAvant = v;
    }

    public String getTaApres() {
        return taApres;
    }

    public void setTaApres(String v) {
        this.taApres = v;
    }

    public Integer getTaSystoliqueAvant() {
        return taSystoliqueAvant;
    }

    public void setTaSystoliqueAvant(Integer v) {
        this.taSystoliqueAvant = v;
    }

    public Integer getTaDiastoliqueAvant() {
        return taDiastoliqueAvant;
    }

    public void setTaDiastoliqueAvant(Integer v) {
        this.taDiastoliqueAvant = v;
    }

    public Integer getFcAvant() {
        return fcAvant;
    }

    public void setFcAvant(Integer v) {
        this.fcAvant = v;
    }

    public BigDecimal getTemperatureAvant() {
        return temperatureAvant;
    }

    public void setTemperatureAvant(BigDecimal v) {
        this.temperatureAvant = v;
    }

    public Integer getEtatGeneralScore() {
        return etatGeneralScore;
    }

    public void setEtatGeneralScore(Integer v) {
        this.etatGeneralScore = v;
    }

    public Boolean getOedemes() {
        return oedemes;
    }

    public void setOedemes(Boolean v) {
        this.oedemes = v;
    }

    public String getOedemesLocalisation() {
        return oedemesLocalisation;
    }

    public void setOedemesLocalisation(String v) {
        this.oedemesLocalisation = v;
    }

    public Boolean getDyspnee() {
        return dyspnee;
    }

    public void setDyspnee(Boolean v) {
        this.dyspnee = v;
    }

    public Integer getQbMlMin() {
        return qbMlMin;
    }

    public void setQbMlMin(Integer v) {
        this.qbMlMin = v;
    }

    public Integer getQdMlMin() {
        return qdMlMin;
    }

    public void setQdMlMin(Integer v) {
        this.qdMlMin = v;
    }

    public Integer getUfCibleMl() {
        return ufCibleMl;
    }

    public void setUfCibleMl(Integer v) {
        this.ufCibleMl = v;
    }

    public Integer getDureePreviMin() {
        return dureePreviMin;
    }

    public void setDureePreviMin(Integer v) {
        this.dureePreviMin = v;
    }

    public Integer getDureeMinutes() {
        return dureeMinutes;
    }

    public void setDureeMinutes(Integer v) {
        this.dureeMinutes = v;
    }

    public Integer getDebitSangMlMin() {
        return debitSangMlMin;
    }

    public void setDebitSangMlMin(Integer v) {
        this.debitSangMlMin = v;
    }

    public BigDecimal getUltrafiltrationMl() {
        return ultrafiltrationMl;
    }

    public void setUltrafiltrationMl(BigDecimal v) {
        this.ultrafiltrationMl = v;
    }

    public String getTypeDialyseur() {
        return typeDialyseur;
    }

    public void setTypeDialyseur(String v) {
        this.typeDialyseur = v;
    }

    public String getTypeBain() {
        return typeBain;
    }

    public void setTypeBain(String v) {
        this.typeBain = v;
    }

    public String getTypeDialysat() {
        return typeDialysat;
    }

    public void setTypeDialysat(String v) {
        this.typeDialysat = v;
    }

    public BigDecimal getConductivite() {
        return conductivite;
    }

    public void setConductivite(BigDecimal v) {
        this.conductivite = v;
    }

    public BigDecimal getTemperatureBain() {
        return temperatureBain;
    }

    public void setTemperatureBain(BigDecimal v) {
        this.temperatureBain = v;
    }

    public String getAnticoagulant() {
        return anticoagulant;
    }

    public void setAnticoagulant(String v) {
        this.anticoagulant = v;
    }

    public String getAnticoagType() {
        return anticoagType;
    }

    public void setAnticoagType(String v) {
        this.anticoagType = v;
    }

    public BigDecimal getAnticoagDoseInitiale() {
        return anticoagDoseInitiale;
    }

    public void setAnticoagDoseInitiale(BigDecimal v) {
        this.anticoagDoseInitiale = v;
    }

    public BigDecimal getAnticoagDoseHoraire() {
        return anticoagDoseHoraire;
    }

    public void setAnticoagDoseHoraire(BigDecimal v) {
        this.anticoagDoseHoraire = v;
    }

    public Integer getNbRincages() {
        return nbRincages;
    }

    public void setNbRincages(Integer v) {
        this.nbRincages = v;
    }

    public Integer getVolumeRincageMl() {
        return volumeRincageMl;
    }

    public void setVolumeRincageMl(Integer v) {
        this.volumeRincageMl = v;
    }

    public String getHeureArretHeparine() {
        return heureArretHeparine;
    }

    public void setHeureArretHeparine(String v) {
        this.heureArretHeparine = v;
    }

    public String getAbordType() {
        return abordType;
    }

    public void setAbordType(String v) {
        this.abordType = v;
    }

    public String getAbordCote() {
        return abordCote;
    }

    public void setAbordCote(String v) {
        this.abordCote = v;
    }

    public String getAiguilleCalibr() {
        return aiguilleCalibr;
    }

    public void setAiguilleCalibr(String v) {
        this.aiguilleCalibr = v;
    }

    public String getOrdrePonction() {
        return ordrePonction;
    }

    public void setOrdrePonction(String v) {
        this.ordrePonction = v;
    }

    public String getAspectSite() {
        return aspectSite;
    }

    public void setAspectSite(String v) {
        this.aspectSite = v;
    }

    public Boolean getIncidentPonction() {
        return incidentPonction;
    }

    public void setIncidentPonction(Boolean v) {
        this.incidentPonction = v;
    }

    public String getIncidentPonctionDetail() {
        return incidentPonctionDetail;
    }

    public void setIncidentPonctionDetail(String v) {
        this.incidentPonctionDetail = v;
    }

    public Integer getTaSystoliqueApres() {
        return taSystoliqueApres;
    }

    public void setTaSystoliqueApres(Integer v) {
        this.taSystoliqueApres = v;
    }

    public Integer getTaDiastoliqueApres() {
        return taDiastoliqueApres;
    }

    public void setTaDiastoliqueApres(Integer v) {
        this.taDiastoliqueApres = v;
    }

    public Integer getFcApres() {
        return fcApres;
    }

    public void setFcApres(Integer v) {
        this.fcApres = v;
    }

    public BigDecimal getKtVRealise() {
        return ktVRealise;
    }

    public void setKtVRealise(BigDecimal v) {
        this.ktVRealise = v;
    }

    public Integer getUfReelleMl() {
        return ufReelleMl;
    }

    public void setUfReelleMl(Integer v) {
        this.ufReelleMl = v;
    }

    public String getIncidents() {
        return incidents;
    }

    public void setIncidents(String v) {
        this.incidents = v;
    }

    public Boolean getIncidentHypotension() {
        return incidentHypotension;
    }

    public void setIncidentHypotension(Boolean v) {
        this.incidentHypotension = v;
    }

    public Boolean getIncidentCrampes() {
        return incidentCrampes;
    }

    public void setIncidentCrampes(Boolean v) {
        this.incidentCrampes = v;
    }

    public Boolean getIncidentCephalees() {
        return incidentCephalees;
    }

    public void setIncidentCephalees(Boolean v) {
        this.incidentCephalees = v;
    }

    public Boolean getIncidentFrissons() {
        return incidentFrissons;
    }

    public void setIncidentFrissons(Boolean v) {
        this.incidentFrissons = v;
    }

    public Boolean getIncidentNausees() {
        return incidentNausees;
    }

    public void setIncidentNausees(Boolean v) {
        this.incidentNausees = v;
    }

    public Boolean getIncidentThrombose() {
        return incidentThrombose;
    }

    public void setIncidentThrombose(Boolean v) {
        this.incidentThrombose = v;
    }

    public String getIncidentAutre() {
        return incidentAutre;
    }

    public void setIncidentAutre(String v) {
        this.incidentAutre = v;
    }

    public String getSignatureInfirmierId() {
        return signatureInfirmierId;
    }

    public void setSignatureInfirmierId(String v) {
        this.signatureInfirmierId = v;
    }

    public OffsetDateTime getSignatureAt() {
        return signatureAt;
    }

    public void setSignatureAt(OffsetDateTime v) {
        this.signatureAt = v;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime v) {
        this.createdAt = v;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime v) {
        this.updatedAt = v;
    }
}


