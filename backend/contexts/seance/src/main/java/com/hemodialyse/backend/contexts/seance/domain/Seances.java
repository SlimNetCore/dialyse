package com.hemodialyse.backend.contexts.seance.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Port sortant : les séances (table {@code seances}), toutes les lectures bornées au centre demandé : une séance d'un autre centre
 * est absente. Un seul dépôt, pour la racine {@link Seance}.
 */
public interface Seances {

    /** Enregistre une nouvelle séance ; l'identifiant est celui de l'agrégat. */
    void ajouter(Seance seance);

    /**
     * Sérialise les scans concurrents d'un même patient pour un même jour, jusqu'à la fin de l'unité de travail : deux scans simultanés ne
     * créent plus deux séances (KB-SEA-02). Verrou non obtenu dans le délai : {@link EcritureConcurrente} (409). Sans effet hors PostgreSQL.
     */
    void verrouillerLeJour(CentreId centre, PatientId patient, LocalDate jour);

    /** Les séances du patient ce jour-là, plus anciennes d'abord (heure de création, sans heure en dernier, puis identifiant) ; vide si aucune. */
    List<Seance> duJour(CentreId centre, PatientId patient, LocalDate jour);

    /** La séance du centre ; inconnue ou d'un autre centre : vide. {@link SeanceIllisible} si la ligne ne se lit pas. */
    Optional<Seance> trouver(CentreId centre, SeanceId id);

    /**
     * Comme {@link #trouver}, en verrouillant la ligne jusqu'à la fin de l'unité de travail (SE2, KB-SEA-18 : deux validations, signatures ou
     * déverrouillages simultanés se suivent). Verrou non obtenu dans le délai : {@link EcritureConcurrente} (409).
     */
    Optional<Seance> trouverPourModifier(CentreId centre, SeanceId id);

    /**
     * Enregistre l'état d'une séance existante après une intention (validation, signature, déverrouillage) : statut, jalons, signataires,
     * déverrouilleur et place. La création reste {@link #ajouter}.
     */
    void enregistrer(Seance seance);

    /**
     * Enregistre le forfait imposé à une séance existante (SE5) : les SEULES colonnes {@code forfait_override_*}, jamais le statut ni
     * les jalons, pour ne pas réécrire, depuis un état lu plus tôt, ce qu'un autre écrivain (la Facturation) aurait changé entre-temps.
     */
    void enregistrerLeForfaitImpose(Seance seance);
}
