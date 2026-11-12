package com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Port publié (lecture) : <b>les compteurs de couverture d'un centre</b> du tableau de bord administratif (patientcouverture.v1,
 * PC5). Bornés au centre. Les bornes {@code du} / {@code au} nulles veulent dire « sans période ». Les comptes reproduisent à
 * l'identique ceux du tableau de bord d'avant (KB-PAT-47) : la période porte sur la date de création de la ligne
 * ({@code created_at}) ; les attestations et prises en charge « à échéance » ne comptent plus les lignes DÉJÀ échues (PL3, KB-PL3-3,
 * à confirmer avec les métiers avant le 2026-12-15).
 */
public interface CompteursDeCouverture {

    /** Les statuts d'une prise en charge (valeurs du JSON et de la colonne {@code statut}, inchangées). */
    enum StatutDePec {
        CREE,
        VALIDEE,
        CLOTUREE
    }

    /** Prises en charge au statut donné, créées dans la période si elle est donnée. */
    long prisesEnCharge(UUID centre, StatutDePec statut, LocalDate du, LocalDate au);

    /**
     * Prises en charge VALIDÉES dont la fin demandée est comprise entre le jour du centre ({@code aujourdhui}) et le seuil, bornes
     * incluses : les prises en charge déjà échues ne sont plus comptées (PL3, KB-PL3-3, décision du 2026-11-11, à confirmer avec les
     * métiers avant le 2026-12-15).
     */
    long prisesEnChargeValideesAEcheance(UUID centre, LocalDate aujourdhui, LocalDate seuil);

    /** Attestations de droits du centre, créées dans la période si elle est donnée. */
    long attestations(UUID centre, LocalDate du, LocalDate au);

    /** Attestations dont la fin est comprise entre le jour du centre et le seuil, bornes incluses, échues exclues (PL3, KB-PL3-3 étendu). */
    long attestationsAEcheance(UUID centre, LocalDate aujourdhui, LocalDate seuil);

    /** Patients créés dans la période (le tableau de bord d'un mois précis). */
    long patientsCrees(UUID centre, LocalDate du, LocalDate au);

    /**
     * L'effectif d'un jour : admis au plus tard ce jour et pas déjà partis avant (règle unique, RG-PAT-032, RG-PAT-037 : un
     * patient dont l'évènement de sortie tombe pendant ou après le jour est compté).
     */
    long patientsPresentsLe(UUID centre, LocalDate jour);
}
