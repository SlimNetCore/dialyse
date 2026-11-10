package com.hemodialyse.backend.contexts.seance.publishedlanguage;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Langage publié de la Séance ({@code seance.v1}, ADR 0098, SE7d) : les séances RÉALISÉES, c'est-à-dire au statut {@code VALIDEE},
 * {@code SIGNEE} ou {@code FACTUREE} en casse exacte, sans blanc (KB-SEA-82, reproduit), lues pour les Absences de patients. Bornées au
 * centre, bornes de dates incluses (bornes inversées : rien). Lecture seule, aucune transaction propre.
 */
public interface SeancesRealiseesPubliees {

    /** Un patient et un jour où il a au moins une séance réalisée. */
    record JourRealisePublie(UUID patient, LocalDate jour) {
    }

    /**
     * Une ligne par patient et par jour réalisé du centre entre ces deux dates incluses, dates croissantes, puis patient (octets non signés de
     * l'identifiant) à date égale : ordre déterministe (KB-SEA-77 corrigé le 2026-11-10, ADR 0117).
     */
    List<JourRealisePublie> joursRealisesDuCentre(UUID centre, LocalDate du, LocalDate au);

    /** Les jours distincts, croissants, où ce patient a une séance réalisée dans le centre entre ces deux dates incluses. */
    List<LocalDate> joursRealisesDuPatient(UUID centre, UUID patient, LocalDate du, LocalDate au);

    /** Vrai si ce patient a au moins une séance réalisée dans le centre ce jour-là. */
    boolean estRealisee(UUID centre, UUID patient, LocalDate jour);

    /**
     * Le patient de chaque séance réalisée du centre ce jour-là : un patient à deux séances réalisées apparaît deux fois ; aucun ordre
     * n'est défini.
     */
    List<UUID> patientsRealisesLe(UUID centre, LocalDate jour);
}
