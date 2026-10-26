package com.hemodialyse.backend.contexts.planification.domain;

import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CreneauId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.InfirmierId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.RemplacementId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.SalleId;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Remplacement ponctuel (racine d'agrégat, entité unique, RG-INF-050 à 052) : à une date, un infirmier éligible renforce une salle sur
 * un créneau, éventuellement à la place d'un collègue (le remplacé). Invariants : jamais soi-même ; jour ouvert et non passé ;
 * remplaçant éligible (preuve {@link RemplacantEligible}) ; remplacé concerné par la case (absent ce jour ou prévu sur la case).
 * Les règles qui portent sur les autres agrégats sont des preuves ou des constats construits par le cas d'usage.
 */
public final class Remplacement {
    private final RemplacementId id;
    private final CentreId centre;
    private final LocalDate date;
    private final SalleId salle;
    private final CreneauId creneau;
    private final InfirmierId remplacant;
    private final InfirmierId remplace;

    private Remplacement(RemplacementId id, CentreId centre, LocalDate date, SalleId salle, CreneauId creneau,
                         InfirmierId remplacant, InfirmierId remplace) {
        if (id == null || centre == null || date == null || salle == null || creneau == null || remplacant == null) {
            throw new SaisieInvalide("Remplacement incomplet");
        }
        this.id = id;
        this.centre = centre;
        this.date = date;
        this.salle = salle;
        this.creneau = creneau;
        this.remplacant = remplacant;
        this.remplace = remplace;
    }

    /**
     * Un infirmier ne se remplace pas lui-même (400) : premier contrôle du cas d'usage (KB-PLN-27).
     */
    public static void personnesDistinctes(InfirmierId remplacant, InfirmierId remplace) {
        if (remplacant != null && remplacant.equals(remplace)) {
            throw new SaisieInvalide("Un infirmier ne peut pas se remplacer lui-même");
        }
    }

    /**
     * Une date passée du centre ou un jour fermé ne reçoit pas de remplaçant (décision du 26/10/2026, KB-PLN-28).
     */
    public static void exigerUneDateDeTravail(LocalDate date, LocalDate aujourdhui, boolean ouvert) {
        if (date.isBefore(aujourdhui)) {
            throw new RegleDePlanificationViolee("REMPLACEMENT_DATE_PASSEE", "Un remplacement ne peut pas être affecté à une date passée");
        }
        if (!ouvert) {
            throw new RegleDePlanificationViolee("REMPLACEMENT_JOUR_FERME", "Le centre est fermé ce jour-là");
        }
    }

    /**
     * Le remplacé nommé est absent ce jour-là ou prévu sur la case ; sinon il n'y a personne à remplacer (KB-PLN-27).
     */
    public static void exigerUnRemplaceConcerne(boolean absentCeJour, boolean prevuSurLaCase) {
        if (!absentCeJour && !prevuSurLaCase) {
            throw new RegleDePlanificationViolee("REMPLACEMENT_REMPLACE_NON_CONCERNE",
                    "L'infirmier remplacé n'est ni absent ce jour-là ni prévu sur cette case");
        }
    }

    public static Remplacement affecter(RemplacementId id, CentreId centre, LocalDate date, SalleId salle, CreneauId creneau,
                                        RemplacantEligible remplacant, InfirmierId remplace) {
        personnesDistinctes(remplacant.infirmier(), remplace);
        return new Remplacement(id, centre, date, salle, creneau, remplacant.infirmier(), remplace);
    }

    /**
     * Un remplacement relu (aucune règle rejouée : une ligne héritée, même incohérente, reste annulable).
     */
    public static Remplacement reconstituer(RemplacementId id, CentreId centre, LocalDate date, SalleId salle, CreneauId creneau,
                                            InfirmierId remplacant, InfirmierId remplace) {
        return new Remplacement(id, centre, date, salle, creneau, remplacant, remplace);
    }

    public RemplacementId id() {
        return id;
    }

    public CentreId centre() {
        return centre;
    }

    public LocalDate date() {
        return date;
    }

    public SalleId salle() {
        return salle;
    }

    public CreneauId creneau() {
        return creneau;
    }

    public InfirmierId remplacant() {
        return remplacant;
    }

    public Optional<InfirmierId> remplace() {
        return Optional.ofNullable(remplace);
    }
}
