package com.hemodialyse.backend.domain.medical.antecedent.aggregate;

import com.hemodialyse.backend.domain.medical.antecedent.event.AntecedentEvent;
import com.hemodialyse.backend.domain.medical.antecedent.valueobject.StatutClinique;
import com.hemodialyse.backend.domain.medical.antecedent.valueobject.TypeAntecedent;
import com.hemodialyse.backend.domain.medical.shared.valueobject.ConceptCode;
import com.hemodialyse.backend.domain.medical.shared.valueobject.PeriodeClinique;
import com.hemodialyse.backend.domain.shared.exception.BusinessException;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Aggregate Root — antécédent ou comorbidité du patient (AGENTS.md §14).
 * <p>
 * Invariants protégés : un diagnostic codé CIM-10 ou, à défaut, un libellé libre est
 * obligatoire (le second existe pour les cas non couverts par le sous-ensemble de codes
 * embarqué — cf. Phase 2 du plan) ; la période est cohérente ({@link PeriodeClinique}) et ne
 * peut pas commencer dans le futur.
 */
public final class Antecedent {

    private final UUID id;
    private final UUID patientId;
    private final UUID centerId;
    private final TypeAntecedent type;
    private final OffsetDateTime createdAt;
    private final List<AntecedentEvent> events = new ArrayList<>();
    private ConceptCode diagnostic;
    private String libelleLibre;
    private PeriodeClinique periode;
    private StatutClinique statutClinique;
    private String severite;
    private String note;
    private OffsetDateTime updatedAt;

    private Antecedent(UUID id, UUID patientId, UUID centerId, TypeAntecedent type, ConceptCode diagnostic,
                       String libelleLibre, PeriodeClinique periode, StatutClinique statutClinique,
                       String severite, String note, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        this.id = id;
        this.patientId = patientId;
        this.centerId = centerId;
        this.type = type;
        this.diagnostic = diagnostic;
        this.libelleLibre = libelleLibre;
        this.periode = periode;
        this.statutClinique = statutClinique;
        this.severite = severite;
        this.note = note;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * Crée un nouvel antécédent, en validant ses invariants et en émettant
     * {@link AntecedentEvent.AntecedentAjoute}.
     */
    public static Antecedent creer(UUID patientId, UUID centerId, TypeAntecedent type, ConceptCode diagnostic,
                                   String libelleLibre, LocalDate dateDebut, LocalDate dateFin,
                                   String severite, String note) {
        if (patientId == null || centerId == null || type == null) {
            throw new BusinessException("ANTECEDENT_CHAMPS_REQUIS", "Patient, centre et type sont obligatoires");
        }
        if (diagnostic == null && (libelleLibre == null || libelleLibre.isBlank())) {
            throw new BusinessException("ANTECEDENT_DIAGNOSTIC_REQUIS",
                    "Un code CIM-10 ou, à défaut, un libellé est obligatoire");
        }
        PeriodeClinique periode = new PeriodeClinique(dateDebut, dateFin);
        if (periode.debut().isAfter(LocalDate.now())) {
            throw new BusinessException("ANTECEDENT_DATE_FUTURE", "La date de début ne peut pas être dans le futur");
        }
        OffsetDateTime now = OffsetDateTime.now();
        Antecedent antecedent = new Antecedent(
                UUID.randomUUID(), patientId, centerId, type, diagnostic,
                libelleLibre == null ? null : libelleLibre.trim(),
                periode, StatutClinique.ACTIF, severite, note, now, now);
        antecedent.events.add(new AntecedentEvent.AntecedentAjoute(antecedent.id, patientId, now));
        return antecedent;
    }

    /**
     * Reconstruction depuis la persistance : aucune ré-émission d'événement, aucune
     * re-validation (les données ont déjà traversé les invariants à la création).
     */
    public static Antecedent reconstituer(UUID id, UUID patientId, UUID centerId, TypeAntecedent type,
                                          ConceptCode diagnostic, String libelleLibre, LocalDate dateDebut,
                                          LocalDate dateFin, StatutClinique statutClinique, String severite,
                                          String note, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        PeriodeClinique periode = new PeriodeClinique(dateDebut, dateFin);
        return new Antecedent(id, patientId, centerId, type, diagnostic, libelleLibre, periode,
                statutClinique, severite, note, createdAt, updatedAt);
    }

    /**
     * Marque l'antécédent comme résolu à la date donnée.
     */
    public void resoudre(LocalDate dateResolution) {
        if (statutClinique == StatutClinique.RESOLU) {
            throw new BusinessException("ANTECEDENT_DEJA_RESOLU", "Cet antécédent est déjà résolu");
        }
        this.periode = new PeriodeClinique(periode.debut(), dateResolution);
        this.statutClinique = StatutClinique.RESOLU;
        this.updatedAt = OffsetDateTime.now();
        this.events.add(new AntecedentEvent.AntecedentResolu(id, patientId, updatedAt));
    }

    public void modifier(ConceptCode diagnostic, String libelleLibre, LocalDate dateDebut, LocalDate dateFin,
                         StatutClinique statutClinique, String severite, String note) {
        if (diagnostic == null && (libelleLibre == null || libelleLibre.isBlank())) {
            throw new BusinessException("ANTECEDENT_DIAGNOSTIC_REQUIS",
                    "Un code CIM-10 ou, à défaut, un libellé est obligatoire");
        }
        this.periode = new PeriodeClinique(dateDebut, dateFin);
        this.diagnostic = diagnostic;
        this.libelleLibre = libelleLibre == null ? null : libelleLibre.trim();
        this.statutClinique = statutClinique == null ? this.statutClinique : statutClinique;
        this.severite = severite;
        this.note = note;
        this.updatedAt = OffsetDateTime.now();
    }

    public List<AntecedentEvent> pullEvents() {
        List<AntecedentEvent> pulled = List.copyOf(events);
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

    public TypeAntecedent getType() {
        return type;
    }

    public ConceptCode getDiagnostic() {
        return diagnostic;
    }

    public String getLibelleLibre() {
        return libelleLibre;
    }

    public PeriodeClinique getPeriode() {
        return periode;
    }

    public StatutClinique getStatutClinique() {
        return statutClinique;
    }

    public String getSeverite() {
        return severite;
    }

    public String getNote() {
        return note;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
