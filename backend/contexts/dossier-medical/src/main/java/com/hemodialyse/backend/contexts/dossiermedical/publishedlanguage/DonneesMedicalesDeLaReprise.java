package com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage;

import java.util.List;
import java.util.UUID;

/**
 * Langage publié du Dossier médical ({@code dossier-medical.v1}, RP2, ADR 0170) : ce que l'annulation d'un lot de reprise de données lit et
 * supprime dans le dossier médical et les analyses (dossier de base, antécédents, sérologies, abords vasculaires, analyses : écrits par le lot ;
 * prescriptions, allergies, observations biologiques, ordonnances, demandes d'examen, administrations d'anémie, bilans pré-greffe : saisis dans
 * la plateforme). Réservé à l'adapter d'annulation de la reprise (règle ArchUnit). N'ouvre aucune transaction : il participe à celle de
 * l'annulation.
 * <p>
 * Une <em>nature</em> est un libellé observable : celui que le refus d'annulation imprime (« patient « P1 » (antecedents_medicaux) ») et que la
 * reprise enregistre ; il est égal au nom historique de la table, mais ce n'est pas un nom de table offert aux appelants : seules les natures
 * de ce contrat sont acceptées (liste fermée, aucune concaténation d'une valeur de l'appelant), toute autre lève
 * {@link IllegalArgumentException}.
 */
public interface DonneesMedicalesDeLaReprise {

    /**
     * Natures du dossier médical contrôlées avant les sorties de stock, dans l'ORDRE du contrôle (le refus nomme la première nature qui bloque
     * : KB-S6a-9 reproduit).
     */
    List<String> NATURES_CONTROLEES_AVANT_LES_BONS_DE_SORTIE = List.of("dossier_medical_patient", "prescriptions_medicales",
            "antecedents_medicaux", "allergies_patient", "serologies_patient", "abords_vasculaires", "resultats_analyses",
            "observations_biologiques", "ordonnances", "demandes_examen", "administrations_anemie");

    /** Natures du dossier médical contrôlées après les sorties de stock (le contrôle des bons de sortie reste au Stock). */
    List<String> NATURES_CONTROLEES_APRES_LES_BONS_DE_SORTIE = List.of("bilans_pre_greffe");

    /** Natures que le lot peut créer, donc supprimer à l'annulation. */
    List<String> NATURES_DU_LOT = List.of("dossier_medical_patient", "antecedents_medicaux", "serologies_patient", "abords_vasculaires",
            "resultats_analyses");

    /**
     * Les identifiants (texte) des lignes de cette nature qui portent le patient, de N'IMPORTE QUEL centre (KB-S6a-9, reproduit : une donnée
     * d'un autre centre pour le même identifiant de patient bloque l'annulation), sans ordre défini.
     */
    List<String> lignesDuPatient(UUID patient, String nature);

    /** Supprime la donnée créée par le lot, seulement dans le centre du lot ; une nature hors {@link #NATURES_DU_LOT} est refusée. */
    void supprimerLaDonneeDuLot(UUID centre, String nature, UUID donnee);
}
