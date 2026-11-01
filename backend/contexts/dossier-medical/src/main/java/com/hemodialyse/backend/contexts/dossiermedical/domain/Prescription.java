package com.hemodialyse.backend.contexts.dossiermedical.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.Instant;
import java.util.UUID;

/**
 * Agrégat {@code Prescription} — prescription médicale (ordonnance) d'un patient.
 */
public final class Prescription {

    private final PrescriptionId id;
    private final UUID patientId;
    private final CentreId centreId;
    private final String medicament;
    private final String posologie;
    private final String duree;
    private final Instant datePrescription;
    private StatutPrescription statut;

    public Prescription(PrescriptionId id, UUID patientId, CentreId centreId, String medicament,
                        String posologie, String duree, Instant datePrescription) {
        if (patientId == null) throw new IllegalArgumentException("patientId requis");
        if (centreId == null) throw new IllegalArgumentException("centreId requis");
        if (medicament == null || medicament.isBlank()) throw new IllegalArgumentException("medicament requis");
        if (posologie == null || posologie.isBlank()) throw new IllegalArgumentException("posologie requise");
        if (duree == null || duree.isBlank()) throw new IllegalArgumentException("duree requise");
        if (datePrescription == null) throw new IllegalArgumentException("datePrescription requise");
        this.id = id;
        this.patientId = patientId;
        this.centreId = centreId;
        this.medicament = medicament;
        this.posologie = posologie;
        this.duree = duree;
        this.datePrescription = datePrescription;
        this.statut = StatutPrescription.BROUILLON;
    }

    public void signer() {
        if (statut != StatutPrescription.BROUILLON) {
            throw new IllegalStateException("PrescriptionDejaSignee");
        }
        this.statut = StatutPrescription.SIGNEE;
    }

    public PrescriptionId id() {
        return id;
    }

    public UUID patientId() {
        return patientId;
    }

    public CentreId centreId() {
        return centreId;
    }

    public String medicament() {
        return medicament;
    }

    public String posologie() {
        return posologie;
    }

    public String duree() {
        return duree;
    }

    public Instant datePrescription() {
        return datePrescription;
    }

    public StatutPrescription statut() {
        return statut;
    }
}