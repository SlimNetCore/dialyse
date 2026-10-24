package com.hemodialyse.backend.contexts.stock.application.inventaire;

import com.hemodialyse.backend.contexts.stock.domain.inventaire.ComptageImporte;
import com.hemodialyse.backend.contexts.stock.domain.inventaire.Inventaire;
import com.hemodialyse.backend.contexts.stock.domain.inventaire.ResultatDImport;

import java.util.List;

/**
 * Port sortant : la feuille de comptage Excel d'un inventaire — génération (à imprimer ou à remplir) et relecture une fois remplie.
 * Implémentée par la plateforme (classeur Excel) ; les refus de fichier (vide, trop gros, format, classeur d'un autre inventaire,
 * colonnes absentes) remontent comme règles métier (422).
 */
public interface FeuilleDeComptage {

    /**
     * @param aveugle sans quantité théorique (bonne pratique : on compte sans connaître le stock informatique)
     */
    byte[] ecrire(Inventaire inventaire, boolean aveugle);

    /**
     * @param referenceAttendue référence de l'inventaire : une feuille d'un autre inventaire est refusée
     */
    Lecture lire(byte[] contenu, String referenceAttendue);

    /**
     * @param anomalies lignes illisibles du fichier (quantité illisible ou négative, motif inconnu)
     */
    record Lecture(List<ComptageImporte> lignes, List<ResultatDImport.Anomalie> anomalies) {
    }
}

