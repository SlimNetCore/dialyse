package com.hemodialyse.backend.contexts.stock.domain.inventaire;

import java.util.List;

/**
 * Bilan de l'import d'une feuille de comptage : l'inventaire mis à jour, le nombre de lignes mises à jour, inchangées et vides, et les
 * lignes refusées (listées sans bloquer les autres).
 */
public record ResultatDImport(Inventaire inventaire, int misesAJour, int inchangees, int vides,
                              List<Anomalie> anomalies) {

    public ResultatDImport {
        anomalies = List.copyOf(anomalies);
    }

    public record Anomalie(int ligneFichier, String message) {
    }
}

