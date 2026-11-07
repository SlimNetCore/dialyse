package com.hemodialyse.backend.contexts.dossiermedical.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Étape de la checklist du bilan pré-greffe d'un patient (table {@code etapes_bilan_pre_greffe}). Racine d'agrégat à une seule entité.
 * <p>
 * Invariants : catégorie et libellé non blanc obligatoires (422 {@code ETAPE_GREFFE_CHAMPS_REQUIS}, reproduit), libellé de 255 caractères
 * au plus (400 au lieu du 500 du legacy) ; une étape {@code FAIT} porte une date de réalisation (422). Le patient, le centre et la
 * catégorie ne changent jamais. Reproduit (KB-MED-93, à confirmer) : la mise à jour REMPLACE tous les champs (champ absent effacé), une
 * date est acceptée pour toute étape, les références {@code demandeExamenId} / {@code serologieId} ne sont pas contrôlées.
 */
public final class EtapeDeBilan {
    private static final int LIBELLE_MAX = 255;

    private final UUID id;
    private final PatientId patient;
    private final CentreId centre;
    private final CategorieDEtape categorie;
    private final String libelle;
    private final Instant creeLe;
    private StatutDEtape statut;
    private LocalDate dateRealisation;
    private String resultat;
    private LocalDate dateExpiration;
    private UUID demandeExamenId;
    private UUID serologieId;
    private Instant modifieLe;

    private EtapeDeBilan(UUID id, PatientId patient, CentreId centre, CategorieDEtape categorie, String libelle, StatutDEtape statut,
                         LocalDate dateRealisation, String resultat, LocalDate dateExpiration, UUID demandeExamenId, UUID serologieId,
                         Instant creeLe, Instant modifieLe) {
        this.id = id;
        this.patient = patient;
        this.centre = centre;
        this.categorie = categorie;
        this.libelle = libelle;
        this.statut = statut;
        this.dateRealisation = dateRealisation;
        this.resultat = resultat;
        this.dateExpiration = dateExpiration;
        this.demandeExamenId = demandeExamenId;
        this.serologieId = serologieId;
        this.creeLe = creeLe;
        this.modifieLe = modifieLe;
    }

    public static EtapeDeBilan ajouter(UUID id, PatientId patient, CentreId centre, CategorieDEtape categorie, String libelle,
                                       Instant maintenant) {
        if (categorie == null || libelle == null || libelle.isBlank()) {
            throw new RegleDeGestionViolee("ETAPE_GREFFE_CHAMPS_REQUIS", "Patient, centre, catégorie et libellé sont obligatoires");
        }
        if (libelle.length() > LIBELLE_MAX) throw new SaisieInvalide("Le libellé dépasse " + LIBELLE_MAX + " caractères");
        return new EtapeDeBilan(id, patient, centre, categorie, libelle, StatutDEtape.A_FAIRE, null, null, null, null, null, maintenant,
                maintenant);
    }

    public static EtapeDeBilan reconstituer(UUID id, PatientId patient, CentreId centre, CategorieDEtape categorie, String libelle,
                                            StatutDEtape statut, LocalDate dateRealisation, String resultat, LocalDate dateExpiration,
                                            UUID demandeExamenId, UUID serologieId, Instant creeLe, Instant modifieLe) {
        return new EtapeDeBilan(id, patient, centre, categorie, libelle, statut, dateRealisation, resultat, dateExpiration, demandeExamenId,
                serologieId, creeLe, modifieLe);
    }

    public void mettreAJour(StatutDEtape statut, LocalDate dateRealisation, String resultat, LocalDate dateExpiration, UUID demandeExamenId,
                            UUID serologieId, Instant maintenant) {
        if (statut == null) throw new RegleDeGestionViolee("ETAPE_GREFFE_STATUT_REQUIS", "Le statut est obligatoire");
        if (statut == StatutDEtape.FAIT && dateRealisation == null) {
            throw new RegleDeGestionViolee("ETAPE_GREFFE_DATE_REALISATION_REQUISE",
                    "Une étape marquée comme faite doit porter une date de réalisation");
        }
        this.statut = statut;
        this.dateRealisation = dateRealisation;
        this.resultat = resultat;
        this.dateExpiration = dateExpiration;
        this.demandeExamenId = demandeExamenId;
        this.serologieId = serologieId;
        this.modifieLe = maintenant;
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

    public CategorieDEtape categorie() {
        return categorie;
    }

    public String libelle() {
        return libelle;
    }

    public StatutDEtape statut() {
        return statut;
    }

    public LocalDate dateRealisation() {
        return dateRealisation;
    }

    public String resultat() {
        return resultat;
    }

    public LocalDate dateExpiration() {
        return dateExpiration;
    }

    public UUID demandeExamenId() {
        return demandeExamenId;
    }

    public UUID serologieId() {
        return serologieId;
    }

    public Instant creeLe() {
        return creeLe;
    }

    public Instant modifieLe() {
        return modifieLe;
    }
}
