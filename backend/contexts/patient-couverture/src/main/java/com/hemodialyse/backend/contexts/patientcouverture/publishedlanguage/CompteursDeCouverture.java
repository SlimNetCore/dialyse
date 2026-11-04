package com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Port publié (lecture) : <b>les compteurs de couverture d'un centre</b> du tableau de bord administratif (patientcouverture.v1,
 * PC5). Bornés au centre. Les bornes {@code du} / {@code au} nulles veulent dire « sans période ». Les comptes reproduisent à
 * l'identique ceux du tableau de bord d'avant (KB-PAT-47) : la période porte sur la date de création de la ligne
 * ({@code created_at}), « à échéance » compte aussi les lignes DÉJÀ échues (à confirmer avec les métiers).
 */
public interface CompteursDeCouverture {

    /** Prises en charge au statut donné ({@code CREE}, {@code VALIDEE}…), créées dans la période si elle est donnée. */
    long prisesEnCharge(UUID centre, String statut, LocalDate du, LocalDate au);

    /** Prises en charge VALIDÉES dont la fin demandée est avant le seuil, bornes incluses, échues comprises. */
    long prisesEnChargeValideesAEcheance(UUID centre, LocalDate seuil);

    /** Attestations de droits du centre, créées dans la période si elle est donnée. */
    long attestations(UUID centre, LocalDate du, LocalDate au);

    /** Attestations dont la fin est avant le seuil, bornes incluses, échues comprises. */
    long attestationsAEcheance(UUID centre, LocalDate seuil);

    /** Patients créés dans la période (le tableau de bord d'un mois précis). */
    long patientsCrees(UUID centre, LocalDate du, LocalDate au);

    /**
     * L'effectif d'un jour : admis au plus tard ce jour et pas déjà partis avant (règle unique, RG-PAT-032, RG-PAT-037 : un
     * patient dont l'évènement de sortie tombe pendant ou après le jour est compté).
     */
    long patientsPresentsLe(UUID centre, LocalDate jour);
}
