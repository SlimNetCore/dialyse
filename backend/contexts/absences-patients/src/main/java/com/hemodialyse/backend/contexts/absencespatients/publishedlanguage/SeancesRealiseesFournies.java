package com.hemodialyse.backend.contexts.absencespatients.publishedlanguage;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Langage publié des Absences de patients, côté FOURNI (ADR 0098, SE7d) : les séances réalisées (validées, signées ou facturées, statut en
 * casse exacte, KB-SEA-82) dont les Absences ont besoin, fournies par la plateforme à partir de {@code seance.v1} (le module ne peut pas
 * dépendre de la Séance : cycle Maven). Bornées au centre, bornes de dates incluses. Lecture seule.
 */
public interface SeancesRealiseesFournies {

    /** Un patient et un jour où il a au moins une séance réalisée. */
    record JourRealiseFourni(UUID patient, LocalDate jour) {
    }

    /** Une ligne par patient et par jour réalisé, dates croissantes ; ordre des patients d'une même date non défini (KB-SEA-77). */
    List<JourRealiseFourni> joursRealisesDuCentre(UUID centre, LocalDate du, LocalDate au);

    /** Les jours distincts, croissants, où ce patient a une séance réalisée. */
    List<LocalDate> joursRealisesDuPatient(UUID centre, UUID patient, LocalDate du, LocalDate au);

    /** Vrai si ce patient a au moins une séance réalisée ce jour-là. */
    boolean estRealisee(UUID centre, UUID patient, LocalDate jour);

    /** Le patient de chaque séance réalisée ce jour-là (doublons possibles, aucun ordre). */
    List<UUID> patientsRealisesLe(UUID centre, LocalDate jour);
}
