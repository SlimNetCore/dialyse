package com.hemodialyse.backend.contexts.seance.publishedlanguage;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Langage publié de la Séance ({@code seance.v1}, ADR 0098, SE7d) : les séances réalisées ({@code VALIDEE}, {@code SIGNEE},
 * {@code FACTUREE}, casse exacte, KB-SEA-82) d'une période, avec la place figée à leur validation, lues pour la grille de la semaine de la
 * Planification. Lecture seule.
 */
public interface SeancesRealiseesAvecLeurPlacePubliees {

    /**
     * Une séance réalisée : place nulle si elle n'a pas été figée ; {@code nom} et {@code prenom} sont ceux de la fiche du patient, BRUTS
     * (aucun blanc retiré, aucune recomposition).
     */
    record SeanceRealiseeAvecPlacePubliee(UUID patient, LocalDate date, UUID salle, UUID creneau, UUID generateur, String nom,
                                          String prenom) {
    }

    /**
     * Les séances réalisées du centre entre ces deux dates incluses, triées par date, puis création (la plus ancienne créée
     * d'abord, création inconnue en dernier), puis identifiant : deux séances d'un même patient le même jour sortent dans un ordre
     * déterministe et la première lue (celle que garde la Planification) est la plus ancienne créée (KB-SEA-78 corrigé le 2026-11-10,
     * ADR 0117). Une séance n'est lue que si la fiche de son patient est dans le centre de la séance (KB-SEA-80, reproduit).
     */
    List<SeanceRealiseeAvecPlacePubliee> duCentre(UUID centre, LocalDate du, LocalDate au);
}
