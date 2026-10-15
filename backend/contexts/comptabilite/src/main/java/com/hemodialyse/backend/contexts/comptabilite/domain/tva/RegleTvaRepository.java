package com.hemodialyse.backend.contexts.comptabilite.domain.tva;

import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;

import java.util.List;

/**
 * Port sortant : les règles de TVA d'un centre, tenues dans la table partagée avec la Facturation ({@code tva_types}). Les
 * règles s'ajoutent seulement : ni modification ni suppression par la Comptabilité.
 */
public interface RegleTvaRepository {

    /**
     * Toutes les règles du centre, actives ou désactivées, tous types, de la plus récente à la plus ancienne (début décroissant).
     */
    List<RegleTva> duCentre(CentreId centre);

    /**
     * Ajoute la règle (active) ; l'identifiant technique est généré ici, côté Java (KB-CPT-21).
     */
    void ajouter(RegleTva regle);
}
