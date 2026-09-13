package com.hemodialyse.backend.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * Ligne d'une ordonnance. FK brute vers {@code ordonnances} (pas de relation JPA mappée —
 * convention du dépôt, cf. AGENTS.md et {@code LigneDemandeExamenJpaEntity}) : chargée et
 * persistée manuellement par l'adapter aux côtés de l'agrégat racine.
 */
@Entity
@Table(name = "lignes_ordonnance")
public class LigneOrdonnanceJpaEntity {

    @Id
    private UUID id;

    @Column(name = "ordonnance_id", nullable = false)
    private UUID ordonnanceId;

    @Column(name = "medicament_code_system", length = 10)
    private String medicamentCodeSystem;

    @Column(name = "medicament_code", length = 20)
    private String medicamentCode;

    @Column(name = "medicament_code_display", length = 255)
    private String medicamentCodeDisplay;

    @Column(name = "libelle", length = 255)
    private String libelle;

    @Column(name = "posologie", nullable = false, length = 255)
    private String posologie;

    @Column(name = "voie", length = 50)
    private String voie;

    @Column(name = "duree_jours")
    private Integer dureeJours;

    @Column(name = "quantite")
    private Integer quantite;

    @Column(name = "instructions", length = 500)
    private String instructions;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getOrdonnanceId() {
        return ordonnanceId;
    }

    public void setOrdonnanceId(UUID ordonnanceId) {
        this.ordonnanceId = ordonnanceId;
    }

    public String getMedicamentCodeSystem() {
        return medicamentCodeSystem;
    }

    public void setMedicamentCodeSystem(String medicamentCodeSystem) {
        this.medicamentCodeSystem = medicamentCodeSystem;
    }

    public String getMedicamentCode() {
        return medicamentCode;
    }

    public void setMedicamentCode(String medicamentCode) {
        this.medicamentCode = medicamentCode;
    }

    public String getMedicamentCodeDisplay() {
        return medicamentCodeDisplay;
    }

    public void setMedicamentCodeDisplay(String medicamentCodeDisplay) {
        this.medicamentCodeDisplay = medicamentCodeDisplay;
    }

    public String getLibelle() {
        return libelle;
    }

    public void setLibelle(String libelle) {
        this.libelle = libelle;
    }

    public String getPosologie() {
        return posologie;
    }

    public void setPosologie(String posologie) {
        this.posologie = posologie;
    }

    public String getVoie() {
        return voie;
    }

    public void setVoie(String voie) {
        this.voie = voie;
    }

    public Integer getDureeJours() {
        return dureeJours;
    }

    public void setDureeJours(Integer dureeJours) {
        this.dureeJours = dureeJours;
    }

    public Integer getQuantite() {
        return quantite;
    }

    public void setQuantite(Integer quantite) {
        this.quantite = quantite;
    }

    public String getInstructions() {
        return instructions;
    }

    public void setInstructions(String instructions) {
        this.instructions = instructions;
    }
}
