package com.hemodialyse.backend.domain.medical.observation.aggregate;

import com.hemodialyse.backend.domain.medical.observation.event.ObservationEvent;
import com.hemodialyse.backend.domain.medical.observation.valueobject.SourceObservation;
import com.hemodialyse.backend.domain.medical.observation.valueobject.StatutObservation;
import com.hemodialyse.backend.domain.medical.observation.valueobject.ValeurMesuree;
import com.hemodialyse.backend.domain.medical.shared.valueobject.CodingSystem;
import com.hemodialyse.backend.domain.medical.shared.valueobject.ConceptCode;
import com.hemodialyse.backend.domain.shared.exception.BusinessException;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Aggregate Root — observation biologique générique codée LOINC (l'équivalent d'une ressource
 * FHIR {@code Observation}). Coexiste avec {@code ResultatAnalyse} (bilan à colonnes fixes) sans
 * le remplacer : voir le plan Phase 4 pour la projection de l'un vers l'autre.
 * <p>
 * Invariants : l'analyte doit être codé LOINC ; la valeur est soit numérique (avec unité), soit
 * textuelle, jamais les deux ni aucune des deux ; une observation dérivée automatiquement d'un
 * bilan ({@link SourceObservation#DERIVEE_BILAN}) est immuable via cette API.
 */
public final class ObservationBiologique {

    private final UUID id;
    private final UUID patientId;
    private final UUID centerId;
    private final UUID demandeExamenId;
    private final ConceptCode analyte;
    private final LocalDate datePrelevement;
    private final SourceObservation source;
    private final OffsetDateTime createdAt;
    private final List<ObservationEvent> events = new ArrayList<>();
    private ValeurMesuree valeurNum;
    private String valeurTexte;
    private StatutObservation statut;
    private OffsetDateTime updatedAt;

    private ObservationBiologique(UUID id, UUID patientId, UUID centerId, UUID demandeExamenId, ConceptCode analyte,
                                  ValeurMesuree valeurNum, String valeurTexte, LocalDate datePrelevement,
                                  StatutObservation statut, SourceObservation source, OffsetDateTime createdAt,
                                  OffsetDateTime updatedAt) {
        this.id = id;
        this.patientId = patientId;
        this.centerId = centerId;
        this.demandeExamenId = demandeExamenId;
        this.analyte = analyte;
        this.valeurNum = valeurNum;
        this.valeurTexte = valeurTexte;
        this.datePrelevement = datePrelevement;
        this.statut = statut;
        this.source = source;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static ObservationBiologique enregistrer(UUID patientId, UUID centerId, UUID demandeExamenId,
                                                    ConceptCode analyte, ValeurMesuree valeurNum, String valeurTexte,
                                                    LocalDate datePrelevement, StatutObservation statut) {
        if (patientId == null || centerId == null || analyte == null || datePrelevement == null) {
            throw new BusinessException("OBSERVATION_CHAMPS_REQUIS",
                    "Patient, centre, analyte et date de prélèvement sont obligatoires");
        }
        if (analyte.system() != CodingSystem.LOINC) {
            throw new BusinessException("OBSERVATION_ANALYTE_NON_LOINC",
                    "Une observation biologique doit être codée LOINC");
        }
        validerValeur(valeurNum, valeurTexte);
        if (datePrelevement.isAfter(LocalDate.now())) {
            throw new BusinessException("OBSERVATION_DATE_FUTURE", "La date de prélèvement ne peut pas être dans le futur");
        }
        OffsetDateTime now = OffsetDateTime.now();
        ObservationBiologique observation = new ObservationBiologique(UUID.randomUUID(), patientId, centerId,
                demandeExamenId, analyte, valeurNum, valeurTexte, datePrelevement,
                statut != null ? statut : StatutObservation.FINAL, SourceObservation.SAISIE_DIRECTE, now, now);
        observation.events.add(new ObservationEvent.ObservationEnregistree(observation.id, patientId, now));
        return observation;
    }

    public static ObservationBiologique reconstituer(UUID id, UUID patientId, UUID centerId, UUID demandeExamenId,
                                                     ConceptCode analyte, ValeurMesuree valeurNum, String valeurTexte,
                                                     LocalDate datePrelevement, StatutObservation statut,
                                                     SourceObservation source, OffsetDateTime createdAt,
                                                     OffsetDateTime updatedAt) {
        return new ObservationBiologique(id, patientId, centerId, demandeExamenId, analyte, valeurNum, valeurTexte,
                datePrelevement, statut, source, createdAt, updatedAt);
    }

    private static void validerValeur(ValeurMesuree valeurNum, String valeurTexte) {
        boolean hasNum = valeurNum != null;
        boolean hasTexte = valeurTexte != null && !valeurTexte.isBlank();
        if (hasNum == hasTexte) {
            throw new BusinessException("OBSERVATION_VALEUR_INVALIDE",
                    "Une observation porte soit une valeur numérique, soit une valeur textuelle, jamais les deux ni aucune");
        }
    }

    /**
     * Corrige la valeur d'une observation saisie directement — interdit sur une observation dérivée.
     */
    public void corriger(ValeurMesuree valeurNum, String valeurTexte) {
        if (source == SourceObservation.DERIVEE_BILAN) {
            throw new BusinessException("OBSERVATION_DERIVEE_IMMUABLE",
                    "Une observation dérivée automatiquement d'un bilan ne peut pas être modifiée ici");
        }
        validerValeur(valeurNum, valeurTexte);
        this.valeurNum = valeurNum;
        this.valeurTexte = valeurTexte;
        this.statut = StatutObservation.CORRIGE;
        this.updatedAt = OffsetDateTime.now();
    }

    public List<ObservationEvent> pullEvents() {
        List<ObservationEvent> pulled = List.copyOf(events);
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

    public Optional<UUID> getDemandeExamenId() {
        return Optional.ofNullable(demandeExamenId);
    }

    public ConceptCode getAnalyte() {
        return analyte;
    }

    public Optional<ValeurMesuree> getValeurNum() {
        return Optional.ofNullable(valeurNum);
    }

    public String getValeurTexte() {
        return valeurTexte;
    }

    public LocalDate getDatePrelevement() {
        return datePrelevement;
    }

    public StatutObservation getStatut() {
        return statut;
    }

    public SourceObservation getSource() {
        return source;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
