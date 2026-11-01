package com.hemodialyse.backend.contexts.patientcouverture.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

public class AttestationDroit {
    private final AttestationDroitId id;
    private final CentreId centreId;
    private final PatientId patientId;

    private final LocalDate dateDebut;
    private final LocalDate dateFin;
    private final OffsetDateTime createdAt;

    public AttestationDroit(AttestationDroitId id, CentreId centreId, PatientId patientId,
                            LocalDate dateDebut, LocalDate dateFin, OffsetDateTime createdAt) {
        if (id == null) throw new IllegalArgumentException("AttestationDroit requis");
        if (centreId == null) throw new IllegalArgumentException("Centre requis");
        if (patientId == null) throw new IllegalArgumentException("Patient requis");
        if (dateDebut == null || dateFin == null) throw new IllegalArgumentException("Dates attestation obligatoires");
        if (dateDebut.isAfter(dateFin)) throw new IllegalArgumentException("Date fin avant date debut");
        this.id = id;
        this.centreId = centreId;
        this.patientId = patientId;
        this.dateDebut = dateDebut;
        this.dateFin = dateFin;
        this.createdAt = createdAt;
    }

    // Compatibility constructor (legacy style)
    public AttestationDroit(UUID id, UUID patientId, UUID centreId, LocalDate dateDebut, LocalDate dateFin) {
        this(new AttestationDroitId(id), new CentreId(centreId), new PatientId(patientId), dateDebut, dateFin, null);
    }

    public static AttestationDroit creer(AttestationDroitId id, PatientId patientId, CentreId centreId,
                                         LocalDate dateDebut, LocalDate dateFin, OffsetDateTime createdAt) {
        return new AttestationDroit(id, centreId, patientId, dateDebut, dateFin, createdAt);
    }

    public static AttestationDroit creer(UUID id, UUID patientId, UUID centreId, LocalDate dateDebut, LocalDate dateFin) {
        return new AttestationDroit(id, patientId, centreId, dateDebut, dateFin);
    }

    public boolean estValideA(LocalDate date) {
        if (date == null) return false;
        return !date.isBefore(dateDebut) && !date.isAfter(dateFin);
    }

    public AttestationDroitId id() {
        return id;
    }

    public CentreId centreId() {
        return centreId;
    }

    public PatientId patientId() {
        return patientId;
    }

    public LocalDate dateDebut() {
        return dateDebut;
    }

    public LocalDate dateFin() {
        return dateFin;
    }

    public OffsetDateTime createdAt() {
        return createdAt;
    }

    // JavaBean-style accessors for compatibility
    public UUID getId() {
        return id.valeur();
    }

    public UUID getPatientId() {
        return patientId.valeur();
    }

    public UUID getCenterId() {
        return centreId.valeur();
    }

    public LocalDate getDateDebut() {
        return dateDebut;
    }

    public LocalDate getDateFin() {
        return dateFin;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AttestationDroit)) return false;
        return Objects.equals(id, ((AttestationDroit) o).id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}