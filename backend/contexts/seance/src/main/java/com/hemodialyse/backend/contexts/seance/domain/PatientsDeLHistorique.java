package com.hemodialyse.backend.contexts.seance.domain;

import java.util.List;
import java.util.UUID;

/**
 * Port sortant : les patients du centre que l'historique des séances rapproche des séances (LEC-PAT iii, ADR 0178). La Séance détient les
 * séances ; Patient et couverture détient la fiche : la recherche de texte, le tri et la collation des noms sont les siens, la Séance garde
 * la jointure, le filtre sur ses propres colonnes et la pagination.
 */
public interface PatientsDeLHistorique {

    /**
     * Les patients DU CENTRE (un patient d'un autre centre est absent) dont le nom, le prénom ou le code contient chaque mot de
     * {@code texte} (nul ou blanc : tous), dans l'ordre demandé ({@code decroissant} inverse le sens). {@code rang} numérote à partir de 1,
     * dans l'ordre rendu, les valeurs distinctes de la clé de tri : deux patients de même rang sont à égalité.
     */
    List<PatientDeLHistorique> duCentre(UUID centre, String texte, Ordre ordre, boolean decroissant);

    enum Ordre {
        NOM_PUIS_PRENOM, CODE, AUCUN
    }

    record PatientDeLHistorique(UUID patient, String code, String nom, String prenom, int rang) {
    }
}
