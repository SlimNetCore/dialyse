package com.hemodialyse.backend.domain.medical.examen.aggregate;

import com.hemodialyse.backend.domain.medical.examen.entity.LigneDemandeExamen;
import com.hemodialyse.backend.domain.medical.examen.event.DemandeExamenEvent;
import com.hemodialyse.backend.domain.medical.examen.specification.TransitionStatutDemandeSpecification;
import com.hemodialyse.backend.domain.medical.examen.valueobject.CategorieExamen;
import com.hemodialyse.backend.domain.medical.examen.valueobject.StatutDemandeExamen;
import com.hemodialyse.backend.domain.shared.exception.BusinessException;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Aggregate Root — demande d'examen (l'équivalent d'une ressource FHIR {@code ServiceRequest}).
 * <p>
 * Frontière transactionnelle : la demande et ses lignes ({@link LigneDemandeExamen}) forment un
 * seul agrégat ; les résultats eux-mêmes vivent dans l'agrégat séparé
 * {@code ObservationBiologique}, reliés par {@code demandeExamenId} — un résultat a un cycle de
 * vie et des règles de correction propres qui n'ont pas à transiter par cet agrégat.
 * <p>
 * Invariant : au moins une ligne à la création ; les transitions de statut suivent strictement
 * {@link TransitionStatutDemandeSpecification}.
 */
public final class DemandeExamen {

    private final UUID id;
    private final UUID patientId;
    private final UUID centerId;
    private final String prescripteurId;
    private final LocalDate dateDemande;
    private final CategorieExamen categorie;
    private final boolean urgent;
    private final String motif;
    private final List<LigneDemandeExamen> lignes;
    private final OffsetDateTime createdAt;
    private final List<DemandeExamenEvent> events = new ArrayList<>();
    private StatutDemandeExamen statut;
    private String conclusion;
    private OffsetDateTime updatedAt;

    private DemandeExamen(UUID id, UUID patientId, UUID centerId, String prescripteurId, LocalDate dateDemande,
                          CategorieExamen categorie, boolean urgent, String motif, StatutDemandeExamen statut,
                          String conclusion, List<LigneDemandeExamen> lignes, OffsetDateTime createdAt,
                          OffsetDateTime updatedAt) {
        this.id = id;
        this.patientId = patientId;
        this.centerId = centerId;
        this.prescripteurId = prescripteurId;
        this.dateDemande = dateDemande;
        this.categorie = categorie;
        this.urgent = urgent;
        this.motif = motif;
        this.statut = statut;
        this.conclusion = conclusion;
        this.lignes = new ArrayList<>(lignes);
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static DemandeExamen creer(UUID patientId, UUID centerId, String prescripteurId, LocalDate dateDemande,
                                      CategorieExamen categorie, boolean urgent, String motif,
                                      List<LigneDemandeExamen> lignes) {
        if (patientId == null || centerId == null || categorie == null) {
            throw new BusinessException("DEMANDE_EXAMEN_CHAMPS_REQUIS", "Patient, centre et catégorie sont obligatoires");
        }
        if (lignes == null || lignes.isEmpty()) {
            throw new BusinessException("DEMANDE_EXAMEN_LIGNE_REQUISE", "Une demande d'examen doit comporter au moins une ligne");
        }
        LocalDate date = dateDemande != null ? dateDemande : LocalDate.now();
        if (date.isAfter(LocalDate.now())) {
            throw new BusinessException("DEMANDE_EXAMEN_DATE_FUTURE", "La date de demande ne peut pas être dans le futur");
        }
        OffsetDateTime now = OffsetDateTime.now();
        DemandeExamen demande = new DemandeExamen(UUID.randomUUID(), patientId, centerId, prescripteurId, date,
                categorie, urgent, motif, StatutDemandeExamen.DEMANDE, null, lignes, now, now);
        demande.events.add(new DemandeExamenEvent.DemandeExamenEmise(demande.id, patientId, now));
        return demande;
    }

    public static DemandeExamen reconstituer(UUID id, UUID patientId, UUID centerId, String prescripteurId,
                                             LocalDate dateDemande, CategorieExamen categorie, boolean urgent,
                                             String motif, StatutDemandeExamen statut, String conclusion,
                                             List<LigneDemandeExamen> lignes, OffsetDateTime createdAt,
                                             OffsetDateTime updatedAt) {
        return new DemandeExamen(id, patientId, centerId, prescripteurId, dateDemande, categorie, urgent, motif,
                statut, conclusion, lignes, createdAt, updatedAt);
    }

    /**
     * Le prélèvement a été effectué.
     */
    public void preleve() {
        transitionner(StatutDemandeExamen.PRELEVE);
    }

    /**
     * Un résultat vient d'être saisi (côté {@code ObservationBiologique}) pour cette demande.
     */
    public void marquerResultatDisponible() {
        transitionner(StatutDemandeExamen.RESULTAT_DISPONIBLE);
        this.events.add(new DemandeExamenEvent.ResultatExamenRecu(id, patientId, updatedAt));
    }

    /**
     * Le médecin valide les résultats et clôture la demande.
     */
    public void valider(String conclusion) {
        transitionner(StatutDemandeExamen.VALIDE);
        this.conclusion = conclusion;
    }

    public void annuler() {
        transitionner(StatutDemandeExamen.ANNULE);
    }

    private void transitionner(StatutDemandeExamen vers) {
        if (!TransitionStatutDemandeSpecification.estAutorisee(statut, vers)) {
            throw new BusinessException("DEMANDE_EXAMEN_TRANSITION_INVALIDE",
                    "Transition de statut invalide : " + statut + " → " + vers);
        }
        this.statut = vers;
        this.updatedAt = OffsetDateTime.now();
    }

    public List<DemandeExamenEvent> pullEvents() {
        List<DemandeExamenEvent> pulled = List.copyOf(events);
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

    public String getPrescripteurId() {
        return prescripteurId;
    }

    public LocalDate getDateDemande() {
        return dateDemande;
    }

    public CategorieExamen getCategorie() {
        return categorie;
    }

    public boolean isUrgent() {
        return urgent;
    }

    public String getMotif() {
        return motif;
    }

    public StatutDemandeExamen getStatut() {
        return statut;
    }

    public String getConclusion() {
        return conclusion;
    }

    public List<LigneDemandeExamen> getLignes() {
        return List.copyOf(lignes);
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
