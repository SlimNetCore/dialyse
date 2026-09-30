package com.hemodialyse.backend.domain.stock.model;

import java.util.List;

/**
 * Bilan de l'import d'une feuille de comptage : les lignes valides sont appliquées, les autres sont signalées.
 *
 * @param inventaire       inventaire après import
 * @param lignesMisesAJour lignes dont le comptage (ou le motif) a changé
 * @param lignesInchangees lignes identiques à la saisie existante
 * @param lignesVides      lignes du fichier sans quantité (non comptées)
 * @param anomalies        lignes du fichier refusées, avec la raison
 */
public record ResultatImportComptage(Inventaire inventaire, int lignesMisesAJour, int lignesInchangees, int lignesVides,
                                     List<Anomalie> anomalies) {

    public ResultatImportComptage {
        anomalies = List.copyOf(anomalies);
    }

    /**
     * Ligne du fichier non appliquée.
     */
    public record Anomalie(int ligneFichier, String message) {
    }
}

