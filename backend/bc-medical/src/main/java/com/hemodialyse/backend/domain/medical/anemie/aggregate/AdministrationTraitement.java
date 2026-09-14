package com.hemodialyse.backend.domain.medical.anemie.aggregate;

import com.hemodialyse.backend.domain.medical.anemie.event.AdministrationTraitementEvent;
import com.hemodialyse.backend.domain.medical.anemie.valueobject.DoseAdministree;
import com.hemodialyse.backend.domain.medical.anemie.valueobject.TypeTraitementAnemie;
import com.hemodialyse.backend.domain.shared.exception.BusinessException;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Aggregate Root — administration réelle d'un traitement de l'anémie (EPO ou fer injectable),
 * l'équivalent d'une ressource FHIR {@code MedicationAdministration}.
 * <p>
 * Distinct de {@code PrescriptionMedicale} : la prescription dit ce qui est prévu, cet agrégat
 * dit ce qui a été fait — un suivi rigoureux de l'anémie exige de savoir si la dose prescrite a
 * réellement été administrée, pas seulement qu'elle a été prescrite.
 * <p>
 * Invariant : une dose administrée est positive et unitée ({@link DoseAdministree}) sauf si le
 * traitement n'a pas été administré, auquel cas un motif est obligatoire.
 */
public final class AdministrationTraitement {

    private final UUID id;
    private final UUID patientId;
    private final UUID centerId;
    private final UUID prescriptionMedicaleId;
    private final TypeTraitementAnemie typeTraitement;
    private final String molecule;
    private final DoseAdministree dose;
    private final String voie;
    private final LocalDate dateAdministration;
    private final UUID seanceId;
    private final String administrePar;
    private final boolean administree;
    private final String motifNonAdministration;
    private final UUID articleId;
    private final java.math.BigDecimal quantiteArticle;
    private final OffsetDateTime createdAt;

    private final List<AdministrationTraitementEvent> events = new ArrayList<>();

    private AdministrationTraitement(UUID id, UUID patientId, UUID centerId, UUID prescriptionMedicaleId,
                                     TypeTraitementAnemie typeTraitement, String molecule, DoseAdministree dose,
                                     String voie, LocalDate dateAdministration, UUID seanceId, String administrePar,
                                     boolean administree, String motifNonAdministration, UUID articleId,
                                     java.math.BigDecimal quantiteArticle, OffsetDateTime createdAt) {
        this.id = id;
        this.patientId = patientId;
        this.centerId = centerId;
        this.prescriptionMedicaleId = prescriptionMedicaleId;
        this.typeTraitement = typeTraitement;
        this.molecule = molecule;
        this.dose = dose;
        this.voie = voie;
        this.dateAdministration = dateAdministration;
        this.seanceId = seanceId;
        this.administrePar = administrePar;
        this.administree = administree;
        this.motifNonAdministration = motifNonAdministration;
        this.articleId = articleId;
        this.quantiteArticle = quantiteArticle;
        this.createdAt = createdAt;
    }

    public static AdministrationTraitement enregistrer(UUID patientId, UUID centerId, UUID prescriptionMedicaleId,
                                                       TypeTraitementAnemie typeTraitement, String molecule,
                                                       DoseAdministree dose, String voie, LocalDate dateAdministration,
                                                       UUID seanceId, String administrePar, boolean administree,
                                                       String motifNonAdministration, UUID articleId,
                                                       java.math.BigDecimal quantiteArticle) {
        if (patientId == null || centerId == null || typeTraitement == null || dateAdministration == null) {
            throw new BusinessException("ADMINISTRATION_CHAMPS_REQUIS",
                    "Patient, centre, type de traitement et date d'administration sont obligatoires");
        }
        if (administree && dose == null) {
            throw new BusinessException("ADMINISTRATION_DOSE_REQUISE",
                    "La dose est obligatoire pour un traitement effectivement administré");
        }
        if (!administree && (motifNonAdministration == null || motifNonAdministration.isBlank())) {
            throw new BusinessException("ADMINISTRATION_MOTIF_REQUIS",
                    "Un motif est obligatoire lorsque le traitement n'a pas été administré");
        }
        OffsetDateTime now = OffsetDateTime.now();
        AdministrationTraitement administration = new AdministrationTraitement(UUID.randomUUID(), patientId,
                centerId, prescriptionMedicaleId, typeTraitement, molecule, administree ? dose : null, voie,
                dateAdministration, seanceId, administrePar, administree, motifNonAdministration,
                administree ? articleId : null, administree ? quantiteArticle : null, now);
        if (administree) {
            administration.events.add(new AdministrationTraitementEvent.TraitementAnemieAdministre(
                    administration.id, patientId, now));
        } else {
            administration.events.add(new AdministrationTraitementEvent.EcartPrescriptionAdministration(
                    administration.id, patientId, now));
        }
        return administration;
    }

    public static AdministrationTraitement reconstituer(UUID id, UUID patientId, UUID centerId,
                                                        UUID prescriptionMedicaleId, TypeTraitementAnemie typeTraitement,
                                                        String molecule, DoseAdministree dose, String voie,
                                                        LocalDate dateAdministration, UUID seanceId,
                                                        String administrePar, boolean administree,
                                                        String motifNonAdministration, UUID articleId,
                                                        java.math.BigDecimal quantiteArticle, OffsetDateTime createdAt) {
        return new AdministrationTraitement(id, patientId, centerId, prescriptionMedicaleId, typeTraitement, molecule,
                dose, voie, dateAdministration, seanceId, administrePar, administree, motifNonAdministration,
                articleId, quantiteArticle, createdAt);
    }

    public List<AdministrationTraitementEvent> pullEvents() {
        List<AdministrationTraitementEvent> pulled = List.copyOf(events);
        events.clear();
        return pulled;
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

    public Optional<UUID> getPrescriptionMedicaleId() {
        return Optional.ofNullable(prescriptionMedicaleId);
    }

    public TypeTraitementAnemie getTypeTraitement() {
        return typeTraitement;
    }

    public String getMolecule() {
        return molecule;
    }

    public Optional<DoseAdministree> getDose() {
        return Optional.ofNullable(dose);
    }

    public String getVoie() {
        return voie;
    }

    public LocalDate getDateAdministration() {
        return dateAdministration;
    }

    public Optional<UUID> getSeanceId() {
        return Optional.ofNullable(seanceId);
    }

    public String getAdministrePar() {
        return administrePar;
    }

    public boolean isAdministree() {
        return administree;
    }

    public String getMotifNonAdministration() {
        return motifNonAdministration;
    }

    public Optional<UUID> getArticleId() {
        return Optional.ofNullable(articleId);
    }

    public Optional<java.math.BigDecimal> getQuantiteArticle() {
        return Optional.ofNullable(quantiteArticle);
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
