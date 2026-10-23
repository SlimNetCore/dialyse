package com.hemodialyse.backend.contexts.stock.domain.sortie;

import com.hemodialyse.backend.contexts.stock.domain.CentreId;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Bon de sortie d'un centre (RG-STK-030 à 033) : référence séquentielle {@code BS-nnnnn}, séance (ou « aucune séance »),
 * patient et poste facultatifs, date de sortie, lignes (lots choisis). Un bon rattaché à une séance garde sa date. Valeur
 * immuable : une correction produit un nouveau bon de même identité, même référence et même heure de saisie.
 */
public final class BonDeSortie {

    /**
     * Séance des bons manuels (aucune séance).
     */
    public static final UUID AUCUNE_SEANCE = UUID.fromString("00000000-0000-0000-0000-000000000000");

    private final UUID id;
    private final CentreId centre;
    private final String reference;
    private final UUID seanceId;
    private final UUID patientId;
    private final String poste;
    private final LocalDate dateSortie;
    private final String creePar;
    private final OffsetDateTime creeLe;
    private final List<LigneDeSortie> lignes;

    private BonDeSortie(UUID id, CentreId centre, String reference, UUID seanceId, UUID patientId, String poste,
                        LocalDate dateSortie, String creePar, OffsetDateTime creeLe, List<LigneDeSortie> lignes) {
        this.id = Objects.requireNonNull(id, "L'identifiant est obligatoire");
        this.centre = Objects.requireNonNull(centre, "Le centre est obligatoire");
        this.reference = Objects.requireNonNull(reference, "La référence est obligatoire");
        this.seanceId = Objects.requireNonNull(seanceId, "La séance est obligatoire");
        this.patientId = patientId;
        this.poste = poste;
        this.dateSortie = dateSortie;
        this.creePar = creePar;
        this.creeLe = creeLe;
        this.lignes = List.copyOf(lignes);
    }

    public static BonDeSortie nouveau(UUID id, CentreId centre, String reference, UUID seanceId, UUID patientId, String poste,
                                      LocalDate dateSortie, String creePar, OffsetDateTime creeLe, List<LigneDeSortie> lignes) {
        Objects.requireNonNull(dateSortie, "La date de sortie est obligatoire");
        return new BonDeSortie(id, centre, reference, seanceId, patientId, poste, dateSortie, creePar, creeLe, lignes);
    }

    /**
     * Un bon tel qu'il est enregistré (aucun contrôle).
     */
    public static BonDeSortie reconstituer(UUID id, CentreId centre, String reference, UUID seanceId, UUID patientId, String poste,
                                           LocalDate dateSortie, String creePar, OffsetDateTime creeLe, List<LigneDeSortie> lignes) {
        return new BonDeSortie(id, centre, reference, seanceId, patientId, poste, dateSortie, creePar, creeLe, lignes);
    }

    public static boolean estLieeAUneSeance(UUID seanceId) {
        return seanceId != null && !AUCUNE_SEANCE.equals(seanceId);
    }

    /**
     * Bon corrigé : mêmes identité, référence et heure de saisie, nouvelles caractéristiques et nouvelles lignes.
     */
    public BonDeSortie corriger(UUID nouvelleSeance, UUID nouveauPatient, String nouveauPoste, LocalDate nouvelleDate,
                                String nouvelAuteur, List<LigneDeSortie> nouvellesLignes) {
        return new BonDeSortie(id, centre, reference, nouvelleSeance, nouveauPatient, nouveauPoste, nouvelleDate, nouvelAuteur,
                creeLe, nouvellesLignes);
    }

    public boolean estLieeAUneSeance() {
        return estLieeAUneSeance(seanceId);
    }

    public UUID id() {
        return id;
    }

    public CentreId centre() {
        return centre;
    }

    public String reference() {
        return reference;
    }

    public UUID seanceId() {
        return seanceId;
    }

    public UUID patientId() {
        return patientId;
    }

    public String poste() {
        return poste;
    }

    public LocalDate dateSortie() {
        return dateSortie;
    }

    public String creePar() {
        return creePar;
    }

    public OffsetDateTime creeLe() {
        return creeLe;
    }

    public List<LigneDeSortie> lignes() {
        return lignes;
    }
}

