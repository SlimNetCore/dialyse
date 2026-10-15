package com.hemodialyse.backend.contexts.comptabilite.publishedlanguage;

import java.util.List;
import java.util.UUID;

/**
 * Langage publié des journaux comptables (RG-CPT-009) : ceux d'un centre, en lecture.
 */
public interface JournauxDuCentre {

    /**
     * Journaux du centre triés par code ; les cinq journaux par défaut tant qu'il n'a rien enregistré.
     */
    List<JournalPublie> journaux(UUID centre);

    record JournalPublie(String code, String libelle, boolean actif) {
    }
}
