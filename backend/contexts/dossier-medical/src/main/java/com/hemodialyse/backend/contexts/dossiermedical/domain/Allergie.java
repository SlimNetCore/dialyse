package com.hemodialyse.backend.contexts.dossiermedical.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Allergie ou intolérance d'un patient (table {@code allergies_patient}). Racine d'agrégat à une seule entité.
 * <p>
 * Invariants : une criticité HAUTE impose des manifestations non blanches (une allergie sévère sans description est une donnée de
 * sécurité incomplète) ; la date de constatation, par défaut le jour de l'horloge, n'est jamais dans le futur ; la substance, la
 * catégorie, le type de réaction et la date de constatation ne changent jamais (seuls la criticité, les manifestations et le statut se
 * corrigent). Bornes de colonne (400 au lieu du 500 du legacy) : code de la substance 20 caractères, libellé d'affichage 255.
 * <p>
 * Reproduit (KB-MED-66, à confirmer avec les métiers et le DPO) : aucune unicité (la même substance peut être déclarée plusieurs fois) ;
 * la correction remplace les manifestations (absentes = effacées) et conserve le statut s'il est absent ; une allergie REFUTEE reste
 * critique. Aucun événement {@code AllergieDeclaree} / {@code AllergieCritiqueDeclaree} : le legacy les collectait sans jamais les
 * publier (KB-MED-67, à confirmer).
 */
public final class Allergie {
    private final UUID id;
    private final PatientId patient;
    private final CentreId centre;
    private final CodeClinique substance;
    private final CategorieDAllergie categorie;
    private final TypeDeReaction typeDeReaction;
    private final LocalDate dateDeConstatation;
    private final Instant creeLe;
    private CriticiteDAllergie criticite;
    private String manifestations;
    private StatutDeVerification statut;
    private Instant modifieLe;

    private Allergie(UUID id, PatientId patient, CentreId centre, CodeClinique substance, CategorieDAllergie categorie,
                     CriticiteDAllergie criticite, TypeDeReaction typeDeReaction, String manifestations, LocalDate dateDeConstatation,
                     StatutDeVerification statut, Instant creeLe, Instant modifieLe) {
        this.id = id;
        this.patient = patient;
        this.centre = centre;
        this.substance = substance;
        this.categorie = categorie;
        this.criticite = criticite;
        this.typeDeReaction = typeDeReaction;
        this.manifestations = manifestations;
        this.dateDeConstatation = dateDeConstatation;
        this.statut = statut;
        this.creeLe = creeLe;
        this.modifieLe = modifieLe;
    }

    /**
     * Déclare une allergie. {@code dateDeConstatation} absente : {@code aujourdhui} (le jour de l'horloge). {@code statut} absent :
     * SUSPECTEE.
     */
    public static Allergie declarer(UUID id, PatientId patient, CentreId centre, CodeClinique substance, CategorieDAllergie categorie,
                                    CriticiteDAllergie criticite, TypeDeReaction typeDeReaction, String manifestations,
                                    LocalDate dateDeConstatation, StatutDeVerification statut, LocalDate aujourdhui, Instant maintenant) {
        verifierLesManifestations(criticite, manifestations);
        LocalDate constatation = dateDeConstatation != null ? dateDeConstatation : aujourdhui;
        if (constatation.isAfter(aujourdhui)) {
            throw new RegleDeGestionViolee("ALLERGIE_DATE_FUTURE", "La date de constatation ne peut pas être dans le futur");
        }
        substance.verifierLesBornes();
        return new Allergie(id, patient, centre, substance, categorie, criticite, typeDeReaction, manifestations, constatation,
                statut != null ? statut : StatutDeVerification.SUSPECTEE, maintenant, maintenant);
    }

    public static Allergie reconstituer(UUID id, PatientId patient, CentreId centre, CodeClinique substance, CategorieDAllergie categorie,
                                        CriticiteDAllergie criticite, TypeDeReaction typeDeReaction, String manifestations,
                                        LocalDate dateDeConstatation, StatutDeVerification statut, Instant creeLe, Instant modifieLe) {
        return new Allergie(id, patient, centre, substance, categorie, criticite, typeDeReaction, manifestations, dateDeConstatation, statut,
                creeLe, modifieLe);
    }

    /** Corrige la criticité, les manifestations (remplacées, même absentes) et le statut (inchangé s'il est absent). */
    public void corriger(CriticiteDAllergie criticite, String manifestations, StatutDeVerification statut, Instant maintenant) {
        verifierLesManifestations(criticite, manifestations);
        this.criticite = criticite;
        this.manifestations = manifestations;
        if (statut != null) this.statut = statut;
        this.modifieLe = maintenant;
    }

    /** Vrai si l'allergie alimente le bandeau des allergies critiques : toute criticité HAUTE, quel que soit le statut (reproduit). */
    public boolean estCritique() {
        return criticite == CriticiteDAllergie.HAUTE;
    }

    private static void verifierLesManifestations(CriticiteDAllergie criticite, String manifestations) {
        if (criticite.exigeDesManifestations() && (manifestations == null || manifestations.isBlank())) {
            throw new RegleDeGestionViolee("ALLERGIE_MANIFESTATIONS_REQUISES", "Une allergie de criticité haute doit décrire ses manifestations");
        }
    }

    public UUID id() {
        return id;
    }

    public PatientId patient() {
        return patient;
    }

    public CentreId centre() {
        return centre;
    }

    public CodeClinique substance() {
        return substance;
    }

    public CategorieDAllergie categorie() {
        return categorie;
    }

    public CriticiteDAllergie criticite() {
        return criticite;
    }

    public TypeDeReaction typeDeReaction() {
        return typeDeReaction;
    }

    public String manifestations() {
        return manifestations;
    }

    public LocalDate dateDeConstatation() {
        return dateDeConstatation;
    }

    public StatutDeVerification statut() {
        return statut;
    }

    public Instant creeLe() {
        return creeLe;
    }

    public Instant modifieLe() {
        return modifieLe;
    }
}
