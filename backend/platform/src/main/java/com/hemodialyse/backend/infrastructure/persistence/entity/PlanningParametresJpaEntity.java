package com.hemodialyse.backend.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Paramétrage du planning d'un centre : jours d'ouverture et salles d'isolement (listes séparées par des virgules).
 */
@Entity
@Table(name = "planning_parametres")
public class PlanningParametresJpaEntity {

    @Id
    @Column(name = "center_id")
    private UUID centerId;

    @Column(name = "jours_ouverts", nullable = false, length = 100)
    private String joursOuverts;

    @Column(name = "salles_isolement", columnDefinition = "TEXT")
    private String sallesIsolement;

    /**
     * Ratio de sécurité ; null pour les lignes créées avant l'introduction du planning de présence (défaut).
     */
    @Column(name = "patients_par_infirmier")
    private Integer patientsParInfirmier;

    /**
     * Patients suivis par poste et par série (capacité théorique) ; null pour les lignes antérieures (défaut).
     */
    @Column(name = "patients_par_poste_serie")
    private Integer patientsParPosteEtSerie;

    @Column(name = "updated_at", nullable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime updatedAt;

    public PlanningParametresJpaEntity() {
    }

    public PlanningParametresJpaEntity(UUID centerId, String joursOuverts, String sallesIsolement,
                                       Integer patientsParInfirmier, Integer patientsParPosteEtSerie,
                                       OffsetDateTime updatedAt) {
        this.patientsParPosteEtSerie = patientsParPosteEtSerie;
        this.centerId = centerId;
        this.joursOuverts = joursOuverts;
        this.sallesIsolement = sallesIsolement;
        this.patientsParInfirmier = patientsParInfirmier;
        this.updatedAt = updatedAt;
    }

    public Integer getPatientsParPosteEtSerie() {
        return patientsParPosteEtSerie;
    }

    public Integer getPatientsParInfirmier() {
        return patientsParInfirmier;
    }

    public UUID getCenterId() {
        return centerId;
    }

    public String getJoursOuverts() {
        return joursOuverts;
    }

    public String getSallesIsolement() {
        return sallesIsolement;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
