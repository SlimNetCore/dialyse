package com.hemodialyse.backend.domain.medical.anemie.aggregate;

import com.hemodialyse.backend.domain.medical.anemie.valueobject.DetailObservance;
import com.hemodialyse.backend.domain.medical.anemie.valueobject.TypeAlerteObservance;
import com.hemodialyse.backend.domain.medical.anemie.valueobject.TypeTraitementAnemie;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Alerte déclenchée quand les administrations réelles d'un traitement de l'anémie (EPO ou fer
 * injectable) sont en dessous de ce qu'attend la prescription — soit sur une
 * période déjà close ({@link TypeAlerteObservance#RETARD_CONSTATE}), soit en anticipation de la
 * fin de la période en cours ({@link TypeAlerteObservance#RAPPEL_ECHEANCE}).
 * <p>
 * {@code dosesAttendues} et {@code dosesAdministrees} sont des <b>quantités de dose</b> (dans l'unité du
 * {@link DetailObservance}) quand la prescription porte une dose, sinon un nombre d'administrations.
 */
public final class AlerteObservance {

    private final UUID id;
    private final UUID patientId;
    private final UUID centerId;
    private final TypeTraitementAnemie typeTraitement;
    private final TypeAlerteObservance type;
    private final LocalDate periodeDebut;
    private final LocalDate periodeFin;
    private final int dosesAttendues;
    private final int dosesAdministrees;
    private final String message;
    private final DetailObservance detail;
    private final OffsetDateTime createdAt;
    private final OffsetDateTime resolvedAt;

    private AlerteObservance(UUID id, UUID patientId, UUID centerId, TypeTraitementAnemie typeTraitement,
                             TypeAlerteObservance type, LocalDate periodeDebut, LocalDate periodeFin,
                             int dosesAttendues, int dosesAdministrees, String message, DetailObservance detail,
                             OffsetDateTime createdAt, OffsetDateTime resolvedAt) {
        this.id = id;
        this.patientId = patientId;
        this.centerId = centerId;
        this.typeTraitement = typeTraitement;
        this.type = type;
        this.periodeDebut = periodeDebut;
        this.periodeFin = periodeFin;
        this.dosesAttendues = dosesAttendues;
        this.dosesAdministrees = dosesAdministrees;
        this.message = message;
        this.detail = detail == null ? DetailObservance.AUCUN : detail;
        this.createdAt = createdAt;
        this.resolvedAt = resolvedAt;
    }

    public static AlerteObservance declencher(UUID patientId, UUID centerId, TypeTraitementAnemie typeTraitement,
                                              TypeAlerteObservance type, LocalDate periodeDebut, LocalDate periodeFin,
                                              int dosesAttendues, int dosesAdministrees, String message) {
        return declencher(patientId, centerId, typeTraitement, type, periodeDebut, periodeFin, dosesAttendues,
                dosesAdministrees, message, DetailObservance.AUCUN);
    }

    public static AlerteObservance declencher(UUID patientId, UUID centerId, TypeTraitementAnemie typeTraitement,
                                              TypeAlerteObservance type, LocalDate periodeDebut, LocalDate periodeFin,
                                              int dosesAttendues, int dosesAdministrees, String message,
                                              DetailObservance detail) {
        return new AlerteObservance(UUID.randomUUID(), patientId, centerId, typeTraitement, type, periodeDebut,
                periodeFin, dosesAttendues, dosesAdministrees, message, detail, OffsetDateTime.now(), null);
    }

    public static AlerteObservance reconstituer(UUID id, UUID patientId, UUID centerId,
                                                TypeTraitementAnemie typeTraitement, TypeAlerteObservance type,
                                                LocalDate periodeDebut, LocalDate periodeFin, int dosesAttendues,
                                                int dosesAdministrees, String message, OffsetDateTime createdAt,
                                                OffsetDateTime resolvedAt) {
        return reconstituer(id, patientId, centerId, typeTraitement, type, periodeDebut, periodeFin, dosesAttendues,
                dosesAdministrees, message, DetailObservance.AUCUN, createdAt, resolvedAt);
    }

    public static AlerteObservance reconstituer(UUID id, UUID patientId, UUID centerId,
                                                TypeTraitementAnemie typeTraitement, TypeAlerteObservance type,
                                                LocalDate periodeDebut, LocalDate periodeFin, int dosesAttendues,
                                                int dosesAdministrees, String message, DetailObservance detail,
                                                OffsetDateTime createdAt, OffsetDateTime resolvedAt) {
        return new AlerteObservance(id, patientId, centerId, typeTraitement, type, periodeDebut, periodeFin,
                dosesAttendues, dosesAdministrees, message, detail, createdAt, resolvedAt);
    }

    public AlerteObservance resoudre() {
        return new AlerteObservance(id, patientId, centerId, typeTraitement, type, periodeDebut, periodeFin,
                dosesAttendues, dosesAdministrees, message, detail, createdAt, OffsetDateTime.now());
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

    public TypeAlerteObservance getType() {
        return type;
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

    public DetailObservance getDetail() {
        return detail;
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
