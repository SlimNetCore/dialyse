package com.hemodialyse.backend.domain.medical.anemie.aggregate;

import com.hemodialyse.backend.domain.medical.anemie.valueobject.TypeTraitementAnemie;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Alerte déclenchée quand les administrations réelles d'un traitement de l'anémie (EPO ou fer
 * injectable), sur la fenêtre glissante correspondant à la fréquence prescrite, sont en dessous
 * du nombre de doses attendues — la prescription du médecin n'est pas respectée.
 */
public final class AlerteObservance {

    private final UUID id;
    private final UUID patientId;
    private final UUID centerId;
    private final TypeTraitementAnemie typeTraitement;
    private final LocalDate periodeDebut;
    private final LocalDate periodeFin;
    private final int dosesAttendues;
    private final int dosesAdministrees;
    private final String message;
    private final OffsetDateTime createdAt;
    private final OffsetDateTime resolvedAt;

    private AlerteObservance(UUID id, UUID patientId, UUID centerId, TypeTraitementAnemie typeTraitement,
                             LocalDate periodeDebut, LocalDate periodeFin, int dosesAttendues,
                             int dosesAdministrees, String message, OffsetDateTime createdAt,
                             OffsetDateTime resolvedAt) {
        this.id = id;
        this.patientId = patientId;
        this.centerId = centerId;
        this.typeTraitement = typeTraitement;
        this.periodeDebut = periodeDebut;
        this.periodeFin = periodeFin;
        this.dosesAttendues = dosesAttendues;
        this.dosesAdministrees = dosesAdministrees;
        this.message = message;
        this.createdAt = createdAt;
        this.resolvedAt = resolvedAt;
    }

    public static AlerteObservance declencher(UUID patientId, UUID centerId, TypeTraitementAnemie typeTraitement,
                                              LocalDate periodeDebut, LocalDate periodeFin, int dosesAttendues,
                                              int dosesAdministrees, String message) {
        return new AlerteObservance(UUID.randomUUID(), patientId, centerId, typeTraitement, periodeDebut,
                periodeFin, dosesAttendues, dosesAdministrees, message, OffsetDateTime.now(), null);
    }

    public static AlerteObservance reconstituer(UUID id, UUID patientId, UUID centerId,
                                                TypeTraitementAnemie typeTraitement, LocalDate periodeDebut,
                                                LocalDate periodeFin, int dosesAttendues, int dosesAdministrees,
                                                String message, OffsetDateTime createdAt, OffsetDateTime resolvedAt) {
        return new AlerteObservance(id, patientId, centerId, typeTraitement, periodeDebut, periodeFin,
                dosesAttendues, dosesAdministrees, message, createdAt, resolvedAt);
    }

    public AlerteObservance resoudre() {
        return new AlerteObservance(id, patientId, centerId, typeTraitement, periodeDebut, periodeFin,
                dosesAttendues, dosesAdministrees, message, createdAt, OffsetDateTime.now());
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

    public TypeTraitementAnemie getTypeTraitement() {
        return typeTraitement;
    }

    public LocalDate getPeriodeDebut() {
        return periodeDebut;
    }

    public LocalDate getPeriodeFin() {
        return periodeFin;
    }

    public int getDosesAttendues() {
        return dosesAttendues;
    }

    public int getDosesAdministrees() {
        return dosesAdministrees;
    }

    public String getMessage() {
        return message;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public Optional<OffsetDateTime> getResolvedAt() {
        return Optional.ofNullable(resolvedAt);
    }

    public boolean isResolved() {
        return resolvedAt != null;
    }
}
