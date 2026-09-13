package com.hemodialyse.backend.domain.medical.allergie.aggregate;

import com.hemodialyse.backend.domain.medical.allergie.event.AllergieEvent;
import com.hemodialyse.backend.domain.medical.allergie.valueobject.CategorieAllergie;
import com.hemodialyse.backend.domain.medical.allergie.valueobject.CriticiteAllergie;
import com.hemodialyse.backend.domain.medical.allergie.valueobject.StatutVerificationAllergie;
import com.hemodialyse.backend.domain.medical.allergie.valueobject.TypeReaction;
import com.hemodialyse.backend.domain.medical.shared.valueobject.ConceptCode;
import com.hemodialyse.backend.domain.shared.exception.BusinessException;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Aggregate Root — allergie ou intolérance du patient (AGENTS.md §14).
 * <p>
 * Invariant central : une criticité {@link CriticiteAllergie#HAUTE} impose de décrire les
 * manifestations observées — une allergie sévère sans description exploitable est une donnée
 * de sécurité incomplète, pas une simple lacune de saisie.
 */
public final class Allergie {

    private final UUID id;
    private final UUID patientId;
    private final UUID centerId;
    private final ConceptCode substance;
    private final CategorieAllergie categorie;
    private final TypeReaction typeReaction;
    private final LocalDate dateConstatation;
    private final OffsetDateTime createdAt;
    private final List<AllergieEvent> events = new ArrayList<>();
    private CriticiteAllergie criticite;
    private String manifestations;
    private StatutVerificationAllergie statutVerification;
    private OffsetDateTime updatedAt;

    private Allergie(UUID id, UUID patientId, UUID centerId, ConceptCode substance, CategorieAllergie categorie,
                     CriticiteAllergie criticite, TypeReaction typeReaction, String manifestations,
                     LocalDate dateConstatation, StatutVerificationAllergie statutVerification,
                     OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        this.id = id;
        this.patientId = patientId;
        this.centerId = centerId;
        this.substance = substance;
        this.categorie = categorie;
        this.criticite = criticite;
        this.typeReaction = typeReaction;
        this.manifestations = manifestations;
        this.dateConstatation = dateConstatation;
        this.statutVerification = statutVerification;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Allergie declarer(UUID patientId, UUID centerId, ConceptCode substance,
                                    CategorieAllergie categorie, CriticiteAllergie criticite,
                                    TypeReaction typeReaction, String manifestations, LocalDate dateConstatation,
                                    StatutVerificationAllergie statutVerification) {
        if (patientId == null || centerId == null || substance == null || categorie == null
                || criticite == null || typeReaction == null) {
            throw new BusinessException("ALLERGIE_CHAMPS_REQUIS",
                    "Patient, centre, substance, catégorie, criticité et type de réaction sont obligatoires");
        }
        validerManifestations(criticite, manifestations);
        LocalDate constatation = dateConstatation != null ? dateConstatation : LocalDate.now();
        if (constatation.isAfter(LocalDate.now())) {
            throw new BusinessException("ALLERGIE_DATE_FUTURE", "La date de constatation ne peut pas être dans le futur");
        }
        OffsetDateTime now = OffsetDateTime.now();
        Allergie allergie = new Allergie(UUID.randomUUID(), patientId, centerId, substance, categorie, criticite,
                typeReaction, manifestations, constatation,
                statutVerification != null ? statutVerification : StatutVerificationAllergie.SUSPECTEE, now, now);
        allergie.events.add(new AllergieEvent.AllergieDeclaree(allergie.id, patientId, now));
        if (criticite == CriticiteAllergie.HAUTE) {
            allergie.events.add(new AllergieEvent.AllergieCritiqueDeclaree(allergie.id, patientId, now));
        }
        return allergie;
    }

    public static Allergie reconstituer(UUID id, UUID patientId, UUID centerId, ConceptCode substance,
                                        CategorieAllergie categorie, CriticiteAllergie criticite,
                                        TypeReaction typeReaction, String manifestations, LocalDate dateConstatation,
                                        StatutVerificationAllergie statutVerification, OffsetDateTime createdAt,
                                        OffsetDateTime updatedAt) {
        return new Allergie(id, patientId, centerId, substance, categorie, criticite, typeReaction,
                manifestations, dateConstatation, statutVerification, createdAt, updatedAt);
    }

    private static void validerManifestations(CriticiteAllergie criticite, String manifestations) {
        if (criticite == CriticiteAllergie.HAUTE && (manifestations == null || manifestations.isBlank())) {
            throw new BusinessException("ALLERGIE_MANIFESTATIONS_REQUISES",
                    "Une allergie de criticité haute doit décrire ses manifestations");
        }
    }

    public void modifier(CriticiteAllergie criticite, String manifestations,
                         StatutVerificationAllergie statutVerification) {
        validerManifestations(criticite, manifestations);
        boolean devientCritique = criticite == CriticiteAllergie.HAUTE && this.criticite != CriticiteAllergie.HAUTE;
        this.criticite = criticite;
        this.manifestations = manifestations;
        this.statutVerification = statutVerification != null ? statutVerification : this.statutVerification;
        this.updatedAt = OffsetDateTime.now();
        if (devientCritique) {
            this.events.add(new AllergieEvent.AllergieCritiqueDeclaree(id, patientId, updatedAt));
        }
    }

    public List<AllergieEvent> pullEvents() {
        List<AllergieEvent> pulled = List.copyOf(events);
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

    public ConceptCode getSubstance() {
        return substance;
    }

    public CategorieAllergie getCategorie() {
        return categorie;
    }

    public CriticiteAllergie getCriticite() {
        return criticite;
    }

    public TypeReaction getTypeReaction() {
        return typeReaction;
    }

    public String getManifestations() {
        return manifestations;
    }

    public LocalDate getDateConstatation() {
        return dateConstatation;
    }

    public StatutVerificationAllergie getStatutVerification() {
        return statutVerification;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
