package com.hemodialyse.backend.domain.pec;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "prise_en_charge")
public class PriseEnCharge {

    @Id
    private UUID id;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "center_id", nullable = false)
    private UUID centerId;

    @Column(name = "date_debut_demande")
    private LocalDate dateDebutDemande;

    @Column(name = "date_fin_demande")
    private LocalDate dateFinDemande;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false)
    private PecStatus status;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected PriseEnCharge() {
        // JPA
    }

    public PriseEnCharge(UUID id, UUID patientId, UUID centerId, LocalDate dateDebutDemande, LocalDate dateFinDemande) {
        this.id = id;
        this.patientId = patientId;
        this.centerId = centerId;
        this.dateDebutDemande = dateDebutDemande;
        this.dateFinDemande = dateFinDemande;
        this.status = PecStatus.CREE;
        this.createdAt = OffsetDateTime.now();
    }

    public void valider() {
        if (status != PecStatus.CREE) {
            throw new IllegalStateException("Transition invalide vers VALIDEE");
        }
        status = PecStatus.VALIDEE;
    }

    public void cloturer() {
        if (status != PecStatus.VALIDEE) {
            throw new IllegalStateException("Transition invalide vers CLOTUREE");
        }
        status = PecStatus.CLOTUREE;
    }

    public boolean autoriseSeance() {
        return status == PecStatus.VALIDEE;
    }

    public UUID getId() {
        return id;
    }

    public UUID getPatientId() {
        return patientId;
    }

    public UUID getCenterId() {
        return centerId;
    }

    public PecStatus getStatus() {
        return status;
    }
}

