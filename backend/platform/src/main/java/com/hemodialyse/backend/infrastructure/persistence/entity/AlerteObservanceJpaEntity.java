package com.hemodialyse.backend.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "alertes_observance")
public class AlerteObservanceJpaEntity {

    @Id
    private UUID id;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "center_id", nullable = false)
    private UUID centerId;

    @Column(name = "type_traitement", nullable = false, length = 20)
    private String typeTraitement;

    @Column(name = "type_alerte", nullable = false, length = 20)
    private String typeAlerte;

    @Column(name = "periode_debut", nullable = false)
    private LocalDate periodeDebut;

    @Column(name = "periode_fin", nullable = false)
    private LocalDate periodeFin;

    @Column(name = "doses_attendues", nullable = false)
    private int dosesAttendues;

    @Column(name = "doses_administrees", nullable = false)
    private int dosesAdministrees;

    @Column(name = "message", length = 500)
    private String message;

    /**
     * Unité des doses de l'alerte (« UI », « mg ») ; vide : elles comptent des administrations.
     */
    @Column(name = "unite_dose", length = 10)
    private String uniteDose;

    @Column(name = "dose_prescrite")
    private Integer dosePrescrite;

    @Column(name = "frequence_valeur")
    private Integer frequenceValeur;

    @Column(name = "frequence_unite", length = 10)
    private String frequenceUnite;

    @Column(name = "created_at", nullable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime createdAt;

    @Column(name = "resolved_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime resolvedAt;

    public String getUniteDose() {
        return uniteDose;
    }

    public void setUniteDose(String uniteDose) {
        this.uniteDose = uniteDose;
    }

    public Integer getDosePrescrite() {
        return dosePrescrite;
    }

    public void setDosePrescrite(Integer dosePrescrite) {
        this.dosePrescrite = dosePrescrite;
    }

    public Integer getFrequenceValeur() {
        return frequenceValeur;
    }

    public void setFrequenceValeur(Integer frequenceValeur) {
        this.frequenceValeur = frequenceValeur;
    }

    public String getFrequenceUnite() {
        return frequenceUnite;
    }

    public void setFrequenceUnite(String frequenceUnite) {
        this.frequenceUnite = frequenceUnite;
    }

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

    public String getTypeTraitement() {
        return typeTraitement;
    }

    public void setTypeTraitement(String typeTraitement) {
        this.typeTraitement = typeTraitement;
    }

    public String getTypeAlerte() {
        return typeAlerte;
    }

    public void setTypeAlerte(String typeAlerte) {
        this.typeAlerte = typeAlerte;
    }

    public LocalDate getPeriodeDebut() {
        return periodeDebut;
    }

    public void setPeriodeDebut(LocalDate periodeDebut) {
        this.periodeDebut = periodeDebut;
    }

    public LocalDate getPeriodeFin() {
        return periodeFin;
    }

    public void setPeriodeFin(LocalDate periodeFin) {
        this.periodeFin = periodeFin;
    }

    public int getDosesAttendues() {
        return dosesAttendues;
    }

    public void setDosesAttendues(int dosesAttendues) {
        this.dosesAttendues = dosesAttendues;
    }

    public int getDosesAdministrees() {
        return dosesAdministrees;
    }

    public void setDosesAdministrees(int dosesAdministrees) {
        this.dosesAdministrees = dosesAdministrees;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(OffsetDateTime resolvedAt) {
        this.resolvedAt = resolvedAt;
    }
}
