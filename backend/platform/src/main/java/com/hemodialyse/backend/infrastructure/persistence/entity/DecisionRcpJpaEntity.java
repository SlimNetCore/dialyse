package com.hemodialyse.backend.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Décision de RCP d'un bilan de greffe. FK brute vers {@code bilans_pre_greffe} (pas de relation
 * JPA mappée — convention du dépôt, cf. {@code LigneDemandeExamenJpaEntity}) : chargée et
 * persistée manuellement par l'adapter aux côtés de l'agrégat racine.
 */
@Entity
@Table(name = "decisions_rcp")
public class DecisionRcpJpaEntity {

    @Id
    private UUID id;

    @Column(name = "bilan_id", nullable = false)
    private UUID bilanId;

    @Column(name = "date_reunion", nullable = false)
    private LocalDate dateReunion;

    @Column(name = "avis", nullable = false, length = 15)
    private String avis;

    @Column(name = "compte_rendu", columnDefinition = "TEXT")
    private String compteRendu;

    @Column(name = "prochaine_date_revue")
    private LocalDate prochaineDateRevue;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getBilanId() {
        return bilanId;
    }

    public void setBilanId(UUID bilanId) {
        this.bilanId = bilanId;
    }

    public LocalDate getDateReunion() {
        return dateReunion;
    }

    public void setDateReunion(LocalDate dateReunion) {
        this.dateReunion = dateReunion;
    }

    public String getAvis() {
        return avis;
    }

    public void setAvis(String avis) {
        this.avis = avis;
    }

    public String getCompteRendu() {
        return compteRendu;
    }

    public void setCompteRendu(String compteRendu) {
        this.compteRendu = compteRendu;
    }

    public LocalDate getProchaineDateRevue() {
        return prochaineDateRevue;
    }

    public void setProchaineDateRevue(LocalDate prochaineDateRevue) {
        this.prochaineDateRevue = prochaineDateRevue;
    }
}
