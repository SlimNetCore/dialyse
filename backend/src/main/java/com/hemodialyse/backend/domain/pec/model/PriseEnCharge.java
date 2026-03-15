package com.hemodialyse.backend.domain.pec.model;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Domain entity — Prise en charge (no JPA). Contains state machine logic. */
public class PriseEnCharge {
    private UUID id;
    private UUID patientId;
    private UUID centerId;

    // Demande
    private LocalDate dateDebutDemande;
    private LocalDate dateFinDemande;
    private UUID forfaitDemandeId;

    // Accord
    private LocalDate dateDebutEffectif;
    private LocalDate dateFinEffectif;
    private UUID forfaitEffectifId;

    private PecStatus status;
    private OffsetDateTime createdAt;

    public PriseEnCharge() {}

    public PriseEnCharge(UUID id, UUID patientId, UUID centerId,
                         LocalDate dateDebutDemande, LocalDate dateFinDemande, UUID forfaitDemandeId) {
        this.id = id;
        this.patientId = patientId;
        this.centerId = centerId;
        this.dateDebutDemande = dateDebutDemande;
        this.dateFinDemande = dateFinDemande;
        this.forfaitDemandeId = forfaitDemandeId;
        this.status = PecStatus.CREE;
        this.createdAt = OffsetDateTime.now();
    }

    public void valider(LocalDate debutEffectif, LocalDate finEffectif, UUID forfaitEffectifId) {
        if (status != PecStatus.CREE) throw new IllegalStateException("Transition invalide vers VALIDEE");
        this.dateDebutEffectif = debutEffectif;
        this.dateFinEffectif = finEffectif;
        this.forfaitEffectifId = forfaitEffectifId;
        this.status = PecStatus.VALIDEE;
    }

    public void cloturer() {
        if (status != PecStatus.VALIDEE) throw new IllegalStateException("Transition invalide vers CLOTUREE");
        this.status = PecStatus.CLOTUREE;
    }

    public boolean autoriseSeance() { return status == PecStatus.VALIDEE; }

    // Getters
    public UUID getId() { return id; }
    public UUID getPatientId() { return patientId; }
    public UUID getCenterId() { return centerId; }
    public LocalDate getDateDebutDemande() { return dateDebutDemande; }
    public LocalDate getDateFinDemande() { return dateFinDemande; }
    public UUID getForfaitDemandeId() { return forfaitDemandeId; }
    public LocalDate getDateDebutEffectif() { return dateDebutEffectif; }
    public LocalDate getDateFinEffectif() { return dateFinEffectif; }
    public UUID getForfaitEffectifId() { return forfaitEffectifId; }
    public PecStatus getStatus() { return status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }

    // Setters for persistence hydration
    public void setId(UUID v) { this.id = v; }
    public void setPatientId(UUID v) { this.patientId = v; }
    public void setCenterId(UUID v) { this.centerId = v; }
    public void setDateDebutDemande(LocalDate v) { this.dateDebutDemande = v; }
    public void setDateFinDemande(LocalDate v) { this.dateFinDemande = v; }
    public void setForfaitDemandeId(UUID v) { this.forfaitDemandeId = v; }
    public void setDateDebutEffectif(LocalDate v) { this.dateDebutEffectif = v; }
    public void setDateFinEffectif(LocalDate v) { this.dateFinEffectif = v; }
    public void setForfaitEffectifId(UUID v) { this.forfaitEffectifId = v; }
    public void setStatus(PecStatus v) { this.status = v; }
    public void setCreatedAt(OffsetDateTime v) { this.createdAt = v; }
}


