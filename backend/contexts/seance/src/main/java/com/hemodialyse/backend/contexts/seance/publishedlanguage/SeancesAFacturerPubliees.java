package com.hemodialyse.backend.contexts.seance.publishedlanguage;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Langage publié de la Séance ({@code seance.v1}, ADR 0097) : les séances à facturer d'un centre. Une séance est à facturer quand son
 * statut est {@code VALIDEE} ou {@code SIGNEE} (comparé en majuscules exactes, KB-FAC-17), qu'elle n'est rattachée à aucune facture et que
 * sa date est dans la période (bornes incluses). Seules les séances d'un patient du même centre sont publiées. Ordre : nom, prénom du
 * patient, date de la séance, identifiant. Lecture seule ; les prises en charge ne sont pas lues ici (elles restent au consommateur).
 */
public interface SeancesAFacturerPubliees {

    List<SeanceAFacturerPubliee> dansLaPeriode(UUID centre, LocalDate debut, LocalDate fin);

    /**
     * @param statut          statut tel qu'enregistré
     * @param forfaitImposeId identifiant du forfait imposé à la séance, {@code null} s'il n'y en a pas (le libellé et le prix le sont alors aussi)
     * @param prixHtImpose    prix hors taxe imposé, copié à l'identique
     */
    record SeanceAFacturerPubliee(UUID id, LocalDate date, String statut, UUID forfaitImposeId, String libelleImpose,
                                  BigDecimal prixHtImpose, PatientDeLaSeancePublie patient) {
    }

    /**
     * Le patient de la séance, tel que la Séance le publie pour la facturation (valeurs brutes, sans interprétation).
     */
    record PatientDeLaSeancePublie(UUID id, String code, String nom, String prenom, String etat, String numeroAssurance,
                                   UUID centrePayeur, UUID agence) {
    }
}
