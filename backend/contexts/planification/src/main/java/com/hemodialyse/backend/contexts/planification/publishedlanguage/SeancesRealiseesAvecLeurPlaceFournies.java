package com.hemodialyse.backend.contexts.planification.publishedlanguage;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Langage publié de la Planification, côté FOURNI (ADR 0098, SE7d) : les séances réalisées (validées, signées ou facturées, casse exacte,
 * KB-SEA-82) d'une période avec la place figée à leur validation et le nom brut de la fiche, fournies par la plateforme à partir de
 * {@code seance.v1} (le module ne peut pas dépendre de la Séance : cycle Maven). Lecture seule.
 */
public interface SeancesRealiseesAvecLeurPlaceFournies {

    /** Une séance réalisée : place nulle si elle n'a pas été figée ; nom et prénom bruts de la fiche du patient. */
    record SeanceRealiseeAvecPlaceFournie(UUID patient, LocalDate date, UUID salle, UUID creneau, UUID generateur, String nom,
                                          String prenom) {
    }

    /**
     * Les séances réalisées du centre entre ces deux dates incluses, triées par date seule (ordre non défini pour une même date,
     * KB-SEA-78) ; seulement si la fiche du patient est dans le centre de la séance (KB-SEA-80).
     */
    List<SeanceRealiseeAvecPlaceFournie> duCentre(UUID centre, LocalDate du, LocalDate au);
}
