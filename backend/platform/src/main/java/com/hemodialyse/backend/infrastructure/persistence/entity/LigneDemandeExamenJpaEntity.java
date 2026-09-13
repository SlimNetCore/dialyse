package com.hemodialyse.backend.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * Ligne d'une demande d'examen. FK brute vers {@code demandes_examen} (pas de relation JPA
 * mappée — convention du dépôt, cf. AGENTS.md et les entités existantes) : chargée et
 * persistée manuellement par l'adapter aux côtés de l'agrégat racine.
 */
@Entity
@Table(name = "lignes_demande_examen")
public class LigneDemandeExamenJpaEntity {

    @Id
    private UUID id;

    @Column(name = "demande_id", nullable = false)
    private UUID demandeId;

    @Column(name = "analyte_code_system", length = 10)
    private String analyteCodeSystem;

    @Column(name = "analyte_code", length = 20)
    private String analyteCode;

    @Column(name = "analyte_code_display", length = 255)
    private String analyteCodeDisplay;

    @Column(name = "libelle", length = 255)
    private String libelle;

    @Column(name = "commentaire", length = 500)
    private String commentaire;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getDemandeId() {
        return demandeId;
    }

    public void setDemandeId(UUID demandeId) {
        this.demandeId = demandeId;
    }

    public String getAnalyteCodeSystem() {
        return analyteCodeSystem;
    }

    public void setAnalyteCodeSystem(String analyteCodeSystem) {
        this.analyteCodeSystem = analyteCodeSystem;
    }

    public String getAnalyteCode() {
        return analyteCode;
    }

    public void setAnalyteCode(String analyteCode) {
        this.analyteCode = analyteCode;
    }

    public String getAnalyteCodeDisplay() {
        return analyteCodeDisplay;
    }

    public void setAnalyteCodeDisplay(String analyteCodeDisplay) {
        this.analyteCodeDisplay = analyteCodeDisplay;
    }

    public String getLibelle() {
        return libelle;
    }

    public void setLibelle(String libelle) {
        this.libelle = libelle;
    }

    public String getCommentaire() {
        return commentaire;
    }

    public void setCommentaire(String commentaire) {
        this.commentaire = commentaire;
    }
}
