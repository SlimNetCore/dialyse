package com.hemodialyse.backend.contexts.comptabilite.domain.tva;

import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;

import java.util.List;
import java.util.Optional;

/**
 * Port sortant : les règles de TVA d'un centre, tenues dans la table {@code tva_types} dont la Comptabilité est propriétaire
 * (ADR 0030). Une règle s'ajoute, se modifie ou se désactive ; elle n'est jamais supprimée.
 */
public interface RegleTvaRepository {

    /**
     * Auteur enregistré pour les règles saisies par l'écran comptable ({@code /comptabilite/regles-tva}).
     */
    String AUTEUR_COMPTABILITE = "comptabilite";

    /**
     * Toutes les règles du centre, actives ou désactivées, tous types, de la plus récente à la plus ancienne (début décroissant).
     */
    List<RegleTva> duCentre(CentreId centre);

    /**
     * La règle de ce centre portant cet identifiant ; vide si elle n'existe pas ou appartient à un autre centre.
     */
    Optional<RegleTva> duCentre(CentreId centre, RegleTvaId id);

    /**
     * Ajoute la règle (active) avec son auteur.
     */
    void ajouter(RegleTva regle, String auteur);

    /**
     * Ajoute une règle saisie par la Comptabilité.
     */
    default void ajouter(RegleTva regle) {
        ajouter(regle, AUTEUR_COMPTABILITE);
    }

    /**
     * Remplace les valeurs saisies d'une règle existante du même centre ; son état, son auteur et sa date de création ne
     * changent pas (KB-FAC-03 corrigé).
     */
    void remplacer(RegleTva regle);

    /**
     * Désactive la règle de ce centre ; sans effet si elle n'existe pas, appartient à un autre centre ou est déjà désactivée
     * (KB-FAC-06, reproduit).
     */
    void desactiver(CentreId centre, RegleTvaId id);
}
