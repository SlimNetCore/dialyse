package com.hemodialyse.backend.domain.medical.ordonnance.aggregate;

import com.hemodialyse.backend.domain.medical.ordonnance.entity.LigneOrdonnance;
import com.hemodialyse.backend.domain.medical.ordonnance.event.OrdonnanceEvent;
import com.hemodialyse.backend.domain.medical.ordonnance.specification.TransitionStatutOrdonnanceSpecification;
import com.hemodialyse.backend.domain.medical.ordonnance.valueobject.StatutOrdonnance;
import com.hemodialyse.backend.domain.shared.exception.BusinessException;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Aggregate Root — ordonnance médicale (l'équivalent d'une ressource FHIR
 * {@code MedicationRequest} par ligne). Indépendante de {@code PrescriptionMedicale}
 * (bc-seance) qui porte les paramètres de séance de dialyse et le traitement EPO/fer — une
 * ordonnance est un document médicamenteux destiné au patient/à la pharmacie, avec un cycle de
 * vie de signature propre.
 * <p>
 * Invariant : au moins une ligne à la création ; une fois {@code SIGNEE}, l'agrégat n'expose
 * plus aucun mutateur de contenu — seules les transitions de statut restent possibles. Corriger
 * une ordonnance signée impose de l'annuler et d'en créer une nouvelle
 * ({@link TransitionStatutOrdonnanceSpecification}).
 */
public final class Ordonnance {

    private final UUID id;
    private final UUID patientId;
    private final UUID centerId;
    private final String medecinId;
    private final LocalDate datePrescription;
    private final List<LigneOrdonnance> lignes;
    private final OffsetDateTime createdAt;
    private final List<OrdonnanceEvent> events = new ArrayList<>();
    private StatutOrdonnance statut;
    private String numero;
    private OffsetDateTime signedAt;
    private OffsetDateTime updatedAt;

    private Ordonnance(UUID id, UUID patientId, UUID centerId, String medecinId, LocalDate datePrescription,
                       StatutOrdonnance statut, String numero, List<LigneOrdonnance> lignes,
                       OffsetDateTime createdAt, OffsetDateTime updatedAt, OffsetDateTime signedAt) {
        this.id = id;
        this.patientId = patientId;
        this.centerId = centerId;
        this.medecinId = medecinId;
        this.datePrescription = datePrescription;
        this.statut = statut;
        this.numero = numero;
        this.lignes = new ArrayList<>(lignes);
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.signedAt = signedAt;
    }

    public static Ordonnance creer(UUID patientId, UUID centerId, String medecinId, LocalDate datePrescription,
                                   List<LigneOrdonnance> lignes) {
        if (patientId == null || centerId == null) {
            throw new BusinessException("ORDONNANCE_CHAMPS_REQUIS", "Patient et centre sont obligatoires");
        }
        if (lignes == null || lignes.isEmpty()) {
            throw new BusinessException("ORDONNANCE_LIGNE_REQUISE", "Une ordonnance doit comporter au moins une ligne");
        }
        LocalDate date = datePrescription != null ? datePrescription : LocalDate.now();
        OffsetDateTime now = OffsetDateTime.now();
        Ordonnance ordonnance = new Ordonnance(UUID.randomUUID(), patientId, centerId, medecinId, date,
                StatutOrdonnance.BROUILLON, null, lignes, now, now, null);
        ordonnance.events.add(new OrdonnanceEvent.OrdonnanceCreee(ordonnance.id, patientId, now));
        return ordonnance;
    }

    public static Ordonnance reconstituer(UUID id, UUID patientId, UUID centerId, String medecinId,
                                          LocalDate datePrescription, StatutOrdonnance statut, String numero,
                                          List<LigneOrdonnance> lignes, OffsetDateTime createdAt,
                                          OffsetDateTime updatedAt, OffsetDateTime signedAt) {
        return new Ordonnance(id, patientId, centerId, medecinId, datePrescription, statut, numero, lignes,
                createdAt, updatedAt, signedAt);
    }

    /**
     * Signature médicale : rend l'ordonnance opposable et lui attribue son numéro définitif.
     * Au-delà de ce point, plus aucun contenu n'est modifiable.
     */
    public void signer(String numero) {
        if (numero == null || numero.isBlank()) {
            throw new BusinessException("ORDONNANCE_NUMERO_REQUIS", "Un numéro est requis pour signer une ordonnance");
        }
        transitionner(StatutOrdonnance.SIGNEE);
        this.numero = numero;
        this.signedAt = updatedAt;
        this.events.add(new OrdonnanceEvent.OrdonnanceSignee(id, patientId, numero, updatedAt));
    }

    /**
     * L'ordonnance signée a été imprimée/remise au patient. Une réimpression n'est pas une
     * nouvelle transition : ce statut n'est atteint qu'une fois.
     */
    public void marquerImprimee() {
        transitionner(StatutOrdonnance.IMPRIMEE);
    }

    public void annuler() {
        transitionner(StatutOrdonnance.ANNULEE);
        this.events.add(new OrdonnanceEvent.OrdonnanceAnnulee(id, patientId, updatedAt));
    }

    private void transitionner(StatutOrdonnance vers) {
        if (!TransitionStatutOrdonnanceSpecification.estAutorisee(statut, vers)) {
            throw new BusinessException("ORDONNANCE_TRANSITION_INVALIDE",
                    "Transition de statut invalide : " + statut + " → " + vers);
        }
        this.statut = vers;
        this.updatedAt = OffsetDateTime.now();
    }

    public List<OrdonnanceEvent> pullEvents() {
        List<OrdonnanceEvent> pulled = List.copyOf(events);
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

    public String getMedecinId() {
        return medecinId;
    }

    public LocalDate getDatePrescription() {
        return datePrescription;
    }

    public StatutOrdonnance getStatut() {
        return statut;
    }

    public String getNumero() {
        return numero;
    }

    public List<LigneOrdonnance> getLignes() {
        return List.copyOf(lignes);
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public OffsetDateTime getSignedAt() {
        return signedAt;
    }
}
