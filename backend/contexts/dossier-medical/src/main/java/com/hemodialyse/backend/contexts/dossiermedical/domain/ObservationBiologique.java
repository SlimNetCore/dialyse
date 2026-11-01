package com.hemodialyse.backend.contexts.dossiermedical.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Agrégat {@code ObservationBiologique} — résultat de biologie d'un patient (slice DM6).
 * <p>
 * Frontière transactionnelle : une observation est un agrégat autonome rattaché à un patient
 * et à un centre. Invariants : valeur et code requis, date d'observation non future,
 * seuils de référence cohérents (bas &lt; haut) quand ils sont renseignés.
 * <p>
 * L'agrégat ne génère jamais d'identifiant ni d'horloge lui-même : l'identifiant et l'instant
 * sont fournis par l'appelant (couche application), conformément à la frontière hexagonale
 * (règle ArchUnit {@code le_domaine_ne_tire_ni_identifiant_ni_heure}).
 */
public final class ObservationBiologique {

    private final ObservationBiologiqueId id;
    private final UUID patientId;
    private final CentreId centreId;
    private final String code;
    private final String libelle;
    private final String valeur;
    private final String unite;
    private final Double seuilBas;
    private final Double seuilHaut;
    private final LocalDate dateObservation;
    private final String prescripteurId;
    private final String laboratoire;
    private final String commentaire;
    private final OffsetDateTime createdAt;
    private final OffsetDateTime updatedAt;

    private ObservationBiologique(ObservationBiologiqueId id, UUID patientId, CentreId centreId,
                                  String code, String libelle, String valeur, String unite,
                                  Double seuilBas, Double seuilHaut, LocalDate dateObservation,
                                  String prescripteurId, String laboratoire, String commentaire,
                                  OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        this.id = id;
        this.patientId = patientId;
        this.centreId = centreId;
        this.code = code;
        this.libelle = libelle;
        this.valeur = valeur;
        this.unite = unite;
        this.seuilBas = seuilBas;
        this.seuilHaut = seuilHaut;
        this.dateObservation = dateObservation;
        this.prescripteurId = prescripteurId;
        this.laboratoire = laboratoire;
        this.commentaire = commentaire;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * Enregistre une nouvelle observation biologique.
     */
    public static ObservationBiologique enregistrer(ObservationBiologiqueId id, UUID patientId,
                                                    CentreId centreId, String code, String libelle,
                                                    String valeur, String unite,
                                                    Double seuilBas, Double seuilHaut,
                                                    LocalDate dateObservation, String prescripteurId,
                                                    String laboratoire, String commentaire,
                                                    OffsetDateTime maintenant) {
        if (id == null) throw new IllegalArgumentException("ObservationBiologiqueId requis");
        if (patientId == null) throw new IllegalArgumentException("patientId requis");
        if (centreId == null) throw new IllegalArgumentException("centreId requis");
        if (code == null || code.isBlank()) throw new IllegalArgumentException("code requis");
        if (valeur == null || valeur.isBlank()) throw new IllegalArgumentException("valeur requise");
        if (dateObservation == null) throw new IllegalArgumentException("dateObservation requise");
        if (maintenant == null) throw new IllegalArgumentException("maintenant requis");
        if (dateObservation.isAfter(LocalDate.now(java.time.ZoneOffset.UTC))) {
            throw new IllegalArgumentException("OBSERVATION_DATE_FUTURE");
        }
        if (seuilBas != null && seuilHaut != null && seuilBas >= seuilHaut) {
            throw new IllegalArgumentException("OBSERVATION_SEUILS_INCOHERENTS");
        }
        return new ObservationBiologique(id, patientId, centreId, code, libelle, valeur, unite,
                seuilBas, seuilHaut, dateObservation, prescripteurId, laboratoire, commentaire,
                maintenant, maintenant);
    }

    /**
     * Reconstitue une observation depuis la persistance.
     */
    public static ObservationBiologique reconstituer(ObservationBiologiqueId id, UUID patientId,
                                                     CentreId centreId, String code, String libelle,
                                                     String valeur, String unite,
                                                     Double seuilBas, Double seuilHaut,
                                                     LocalDate dateObservation, String prescripteurId,
                                                     String laboratoire, String commentaire,
                                                     OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        return new ObservationBiologique(id, patientId, centreId, code, libelle, valeur, unite,
                seuilBas, seuilHaut, dateObservation, prescripteurId, laboratoire, commentaire,
                createdAt, updatedAt);
    }

    public ObservationBiologiqueId id() {
        return id;
    }

    public UUID patientId() {
        return patientId;
    }

    public CentreId centreId() {
        return centreId;
    }

    public String code() {
        return code;
    }

    public String libelle() {
        return libelle;
    }

    public String valeur() {
        return valeur;
    }

    public String unite() {
        return unite;
    }

    public Double seuilBas() {
        return seuilBas;
    }

    public Double seuilHaut() {
        return seuilHaut;
    }

    public LocalDate dateObservation() {
        return dateObservation;
    }

    public String prescripteurId() {
        return prescripteurId;
    }

    public String laboratoire() {
        return laboratoire;
    }

    public String commentaire() {
        return commentaire;
    }

    public OffsetDateTime createdAt() {
        return createdAt;
    }

    public OffsetDateTime updatedAt() {
        return updatedAt;
    }

    /**
     * Liste des identifiants de centres concernés par cet agrégat (ici un seul : centreId).
     * Utilisé pour le contrôle d'isolation multi-centre.
     */
    public List<CentreId> centresConcernees() {
        return Collections.singletonList(centreId);
    }
}
