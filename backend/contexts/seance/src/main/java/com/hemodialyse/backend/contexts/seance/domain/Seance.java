package com.hemodialyse.backend.contexts.seance.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Séance : acte de dialyse d'un patient, un jour donné, dans un centre (racine d'agrégat, une seule entité). SE1 porte la CRÉATION
 * (statut {@code CREE}, éventuellement confirmée hors planning) et la restitution des lignes existantes ; la validation, la signature,
 * le forfait et le verrou de facturation (SE2 à SE6) restent au legacy tant que leurs slices ne sont pas reprises.
 * <p>
 * Aucune règle d'unicité par patient et par jour : plusieurs séances le même jour restent permises (KB-SEA-02, reproduit).
 */
public final class Seance {

    private final SeanceId id;
    private final CentreId centre;
    private final PatientId patient;
    private final LocalDate jour;
    private final StatutDeSeance statut;
    private final Instant creeLe;
    private final DerogationHorsPlanning derogation;
    private final Jalons jalons;

    private Seance(SeanceId id, CentreId centre, PatientId patient, LocalDate jour, StatutDeSeance statut, Instant creeLe,
                   DerogationHorsPlanning derogation, Jalons jalons) {
        this.id = exiger(id, "Séance requise");
        this.centre = exiger(centre, "Centre requis");
        this.patient = exiger(patient, "Patient requis");
        this.jour = exiger(jour, "Jour de la séance requis");
        this.statut = exiger(statut, "Statut requis");
        this.creeLe = creeLe;
        this.derogation = derogation;
        this.jalons = jalons == null ? Jalons.AUCUN : jalons;
    }

    /** Crée une séance programmée (patient attendu) : statut {@code CREE}, sans dérogation. */
    public static Seance creer(SeanceId id, CentreId centre, PatientId patient, LocalDate jour, Instant creeLe) {
        return new Seance(id, centre, patient, jour, StatutDeSeance.CREE, exiger(creeLe, "Heure de création requise"), null, null);
    }

    /** Crée une séance confirmée hors planning : le motif (et sa précision) est inscrit sur la séance. */
    public static Seance creerHorsPlanning(SeanceId id, CentreId centre, PatientId patient, LocalDate jour, Instant creeLe,
                                           DerogationHorsPlanning derogation) {
        return new Seance(id, centre, patient, jour, StatutDeSeance.CREE, exiger(creeLe, "Heure de création requise"),
                exiger(derogation, "Dérogation requise"), null);
    }

    /** Restitue une ligne existante (adapter de persistance) : aucune règle de création n'est rejouée. */
    public static Seance restituer(SeanceId id, CentreId centre, PatientId patient, LocalDate jour, StatutDeSeance statut,
                                   Instant creeLe, DerogationHorsPlanning derogation, Jalons jalons) {
        return new Seance(id, centre, patient, jour, statut, creeLe, derogation, jalons);
    }

    public SeanceId id() {
        return id;
    }

    public CentreId centre() {
        return centre;
    }

    public PatientId patient() {
        return patient;
    }

    public LocalDate jour() {
        return jour;
    }

    public StatutDeSeance statut() {
        return statut;
    }

    /** Heure de création ; peut être nulle sur une ligne historique (colonne sans contrainte). */
    public Instant creeLe() {
        return creeLe;
    }

    public boolean horsPlanning() {
        return derogation != null;
    }

    public Optional<DerogationHorsPlanning> derogation() {
        return Optional.ofNullable(derogation);
    }

    public Jalons jalons() {
        return jalons;
    }

    /** Vrai tant que la séance n'a pas été validée par l'infirmier (le scan la valide alors, la secrétaire la laisse). */
    public boolean attendLaValidation() {
        return statut == StatutDeSeance.CREE;
    }

    private static <T> T exiger(T valeur, String message) {
        if (valeur == null) throw new SaisieInvalide(message);
        return valeur;
    }

    /**
     * Jalons de la vie de la séance après sa création (lecture seule pour SE1 ; écrits par le legacy jusqu'à SE2) : validation,
     * signature de l'infirmier, signature du médecin, déverrouillage pour régularisation. Tous peuvent être nuls.
     */
    public record Jalons(Instant valideeLe, Instant signeeParLInfirmierLe, Instant signeeParLeMedecinLe,
                         Instant deverrouilleePourRegularisationLe) {
        public static final Jalons AUCUN = new Jalons(null, null, null, null);
    }
}
