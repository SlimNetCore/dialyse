package com.hemodialyse.backend.contexts.dossiermedical.domain;

import com.hemodialyse.backend.contexts.dossiermedical.domain.event.AntecedentEvent;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Aggregate Root — antécédent ou comorbidité d'un patient (AGENTS.md §14).
 * <p>
 * Invariants protégés :
 * <ul>
 *     <li>le patient, le centre et le type sont obligatoires ;</li>
 *     <li>un diagnostic codé (CIM-10) ou, à défaut, un libellé libre est obligatoire ;</li>
 *     <li>la période est cohérente ({@link PeriodeClinique}) et son début ne peut pas être dans le futur ;</li>
 *     <li>un antécédent déjà résolu ne peut pas être résolu à nouveau.</li>
 * </ul>
 * <p>
 * L'agrégat ne génère jamais d'identifiant ni d'horloge lui-même : l'identifiant et l'instant de
 * référence sont fournis par l'appelant (couche application), conformément à la frontière
 * hexagonale (règle ArchUnit {@code le_domaine_ne_tire_ni_identifiant_ni_heure}).
 */
public final class Antecedent {

    private final AntecedentId id;
    private final UUID patientId;
    private final CentreId centreId;
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

    private Antecedent(AntecedentId id, UUID patientId, CentreId centreId, TypeAntecedent type,
                       ConceptCode diagnostic, String libelleLibre, PeriodeClinique periode,
                       StatutClinique statutClinique, String severite, String note,
                       OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        this.id = id;
        this.patientId = patientId;
        this.centreId = centreId;
        this.type = type;
        this.diagnostic = diagnostic;
        this.libelleLibre = libelleLibre == null ? null : libelleLibre.trim();
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
     *
     * @param maintenant instant de référence fourni par l'application (jamais {@code OffsetDateTime.now()}
     *                   dans le domaine).
     */
    public static Antecedent creer(AntecedentId id, UUID patientId, CentreId centreId, TypeAntecedent type,
                                   ConceptCode diagnostic, String libelleLibre, LocalDate dateDebut,
                                   LocalDate dateFin, String severite, String note, OffsetDateTime maintenant) {
        if (id == null) {
            throw new IllegalArgumentException("ANTECEDENT_ID_REQUIS");
        }
        if (patientId == null || centreId == null || type == null) {
            throw new IllegalArgumentException("ANTECEDENT_CHAMPS_REQUIS");
        }
        if (diagnostic == null && (libelleLibre == null || libelleLibre.isBlank())) {
            throw new IllegalArgumentException("ANTECEDENT_DIAGNOSTIC_REQUIS");
        }
        PeriodeClinique periode = new PeriodeClinique(dateDebut, dateFin);
        if (periode.debut().isAfter(maintenant.toLocalDate())) {
            throw new IllegalArgumentException("ANTECEDENT_DATE_FUTURE");
        }
        OffsetDateTime maintenantOd = maintenant;
        Antecedent antecedent = new Antecedent(id, patientId, centreId, type, diagnostic, libelleLibre, periode,
                StatutClinique.ACTIF, severite, note, maintenantOd, maintenantOd);
        antecedent.events.add(new AntecedentEvent.AntecedentAjoute(antecedent.id.value(), patientId, maintenantOd));
        return antecedent;
    }

    /**
     * Reconstruction depuis la persistance : aucune ré-émission d'événement, aucune
     * re-validation (les données ont déjà traversé les invariants à la création).
     */
    public static Antecedent reconstituer(AntecedentId id, UUID patientId, CentreId centreId, TypeAntecedent type,
                                          ConceptCode diagnostic, String libelleLibre, LocalDate dateDebut,
                                          LocalDate dateFin, StatutClinique statutClinique, String severite,
                                          String note, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        PeriodeClinique periode = new PeriodeClinique(dateDebut, dateFin);
        return new Antecedent(id, patientId, centreId, type, diagnostic, libelleLibre, periode,
                statutClinique, severite, note, createdAt, updatedAt);
    }

    /**
     * Marque l'antécédent comme résolu à la date donnée.
     *
     * @param maintenant instant de référence fourni par l'application.
     */
    public void resoudre(LocalDate dateResolution, OffsetDateTime maintenant) {
        if (statutClinique == StatutClinique.RESOLU) {
            throw new IllegalArgumentException("ANTECEDENT_DEJA_RESOLU");
        }
        this.periode = new PeriodeClinique(periode.debut(), dateResolution);
        this.statutClinique = StatutClinique.RESOLU;
        this.updatedAt = maintenant;
        this.events.add(new AntecedentEvent.AntecedentResolu(id.value(), patientId, maintenant));
    }

    /**
     * Modifie les champs de l'antécédent.
     */
    public void modifier(ConceptCode diagnostic, String libelleLibre, LocalDate dateDebut, LocalDate dateFin,
                         StatutClinique statutClinique, String severite, String note, OffsetDateTime maintenant) {
        if (diagnostic == null && (libelleLibre == null || libelleLibre.isBlank())) {
            throw new IllegalArgumentException("ANTECEDENT_DIAGNOSTIC_REQUIS");
        }
        this.periode = new PeriodeClinique(dateDebut, dateFin);
        this.diagnostic = diagnostic;
        this.libelleLibre = libelleLibre == null ? null : libelleLibre.trim();
        this.statutClinique = statutClinique == null ? this.statutClinique : statutClinique;
        this.severite = severite;
        this.note = note;
        this.updatedAt = maintenant;
    }

    public List<AntecedentEvent> pullEvents() {
        List<AntecedentEvent> pulled = List.copyOf(events);
        events.clear();
        return pulled;
    }

    public AntecedentId getId() {
        return id;
    }

    public UUID getPatientId() {
        return patientId;
    }

    public CentreId getCentreId() {
        return centreId;
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

    /**
     * Liste des identifiants de centres concernés par cet agrégat (ici un seul : centreId).
     * Utilisé pour le contrôle d'isolation multi-centre (AGENTS.md §2).
     */
    public List<CentreId> centresConcernees() {
        return Collections.singletonList(centreId);
    }
}
