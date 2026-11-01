package com.hemodialyse.backend.contexts.dossiermedical.domain;

import com.hemodialyse.backend.contexts.dossiermedical.domain.event.AllergieEvent;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Aggregate Root — allergie d'un patient (AGENTS.md §14).
 * <p>
 * Invariants protégés :
 * <ul>
 *     <li>le patient, le centre, l'allergène et le type sont obligatoires ;</li>
 *     <li>la période est cohérente ({@link PeriodeClinique}) et son début ne peut pas être dans le futur ;</li>
 *     <li>une allergie déjà résolue ne peut pas être résolue à nouveau.</li>
 * </ul>
 * <p>
 * L'agrégat ne génère jamais d'identifiant ni d'horloge lui-même : l'identifiant et l'instant de
 * référence sont fournis par l'appelant (couche application).
 */
public final class Allergie {

    private final AllergieId id;
    private final UUID patientId;
    private final CentreId centreId;
    private final String allergene;
    private final TypeAllergie type;
    private final OffsetDateTime createdAt;
    private final List<AllergieEvent> events = new ArrayList<>();
    private String reaction;
    private PeriodeClinique periode;
    private StatutClinique statutClinique;
    private String severite;
    private String note;
    private OffsetDateTime updatedAt;

    private Allergie(AllergieId id, UUID patientId, CentreId centreId, String allergene, TypeAllergie type,
                     String reaction, PeriodeClinique periode, StatutClinique statutClinique, String severite,
                     String note, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        this.id = id;
        this.patientId = patientId;
        this.centreId = centreId;
        this.allergene = allergene;
        this.type = type;
        this.reaction = reaction;
        this.periode = periode;
        this.statutClinique = statutClinique;
        this.severite = severite;
        this.note = note;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * Déclare une nouvelle allergie, en validant ses invariants et en émettant
     * {@link AllergieEvent.AllergieDeclaree}.
     *
     * @param maintenant instant de référence fourni par l'application.
     */
    public static Allergie declarer(AllergieId id, UUID patientId, CentreId centreId, String allergene,
                                    TypeAllergie type, String reaction, LocalDate dateDebut, LocalDate dateFin,
                                    String severite, String note, OffsetDateTime maintenant) {
        if (id == null) {
            throw new IllegalArgumentException("ALLERGIE_ID_REQUIS");
        }
        if (patientId == null || centreId == null || allergene == null || allergene.isBlank() || type == null) {
            throw new IllegalArgumentException("ALLERGIE_CHAMPS_REQUIS");
        }
        PeriodeClinique periode = new PeriodeClinique(dateDebut, dateFin);
        if (periode.debut().isAfter(maintenant.toLocalDate())) {
            throw new IllegalArgumentException("ALLERGIE_DATE_FUTURE");
        }
        OffsetDateTime maintenantOd = maintenant;
        Allergie allergie = new Allergie(id, patientId, centreId, allergene.trim(), type, reaction, periode,
                StatutClinique.ACTIF, severite, note, maintenantOd, maintenantOd);
        allergie.events.add(new AllergieEvent.AllergieDeclaree(allergie.id.value(), patientId, maintenantOd));
        return allergie;
    }

    /**
     * Reconstruction depuis la persistance : aucune ré-émission d'événement, aucune re-validation.
     */
    public static Allergie reconstituer(AllergieId id, UUID patientId, CentreId centreId, String allergene,
                                        TypeAllergie type, String reaction, LocalDate dateDebut, LocalDate dateFin,
                                        StatutClinique statutClinique, String severite, String note,
                                        OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        PeriodeClinique periode = new PeriodeClinique(dateDebut, dateFin);
        return new Allergie(id, patientId, centreId, allergene, type, reaction, periode, statutClinique,
                severite, note, createdAt, updatedAt);
    }

    /**
     * Marque l'allergie comme résolue à la date donnée.
     *
     * @param maintenant instant de référence fourni par l'application.
     */
    public void resoudre(LocalDate dateResolution, OffsetDateTime maintenant) {
        if (statutClinique == StatutClinique.RESOLU) {
            throw new IllegalArgumentException("ALLERGIE_DEJA_RESOLUE");
        }
        this.periode = new PeriodeClinique(periode.debut(), dateResolution);
        this.statutClinique = StatutClinique.RESOLU;
        this.updatedAt = maintenant;
        this.events.add(new AllergieEvent.AllergieResolue(id.value(), patientId, maintenant));
    }

    public void modifier(String reaction, LocalDate dateDebut, LocalDate dateFin,
                         StatutClinique statutClinique, String severite, String note, OffsetDateTime maintenant) {
        this.periode = new PeriodeClinique(dateDebut, dateFin);
        this.reaction = reaction;
        this.statutClinique = statutClinique == null ? this.statutClinique : statutClinique;
        this.severite = severite;
        this.note = note;
        this.updatedAt = maintenant;
    }

    public List<AllergieEvent> pullEvents() {
        List<AllergieEvent> pulled = List.copyOf(events);
        events.clear();
        return pulled;
    }

    public AllergieId getId() {
        return id;
    }

    public UUID getPatientId() {
        return patientId;
    }

    public CentreId getCentreId() {
        return centreId;
    }

    public String getAllergene() {
        return allergene;
    }

    public TypeAllergie getType() {
        return type;
    }

    public String getReaction() {
        return reaction;
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
