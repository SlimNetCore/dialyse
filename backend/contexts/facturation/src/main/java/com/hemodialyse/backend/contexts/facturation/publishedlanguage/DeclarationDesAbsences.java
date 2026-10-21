package com.hemodialyse.backend.contexts.facturation.publishedlanguage;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Langage publié (besoin de la Facturation, fourni par le module Absences, hors contexte) : déclarer et retirer l'absence d'un
 * patient pour une séance exclue de la facturation. Le motif est le nom du motif d'absence de la Facturation ; le module
 * Absences en possède un de même nom pour chacun. Les règles du module Absences s'appliquent : une déclaration refusée (délai
 * de 60 jours, date future, période déjà facturée, absence déjà déclarée à cette date) lève sa propre erreur métier (422), qui
 * annule l'exclusion.
 */
public interface DeclarationDesAbsences {

    /**
     * Déclare l'absence du patient à la date de sa séance et rend son identité.
     *
     * @param auteur l'utilisateur de la session, tracé par l'absence
     */
    UUID declarer(UUID centre, UUID patient, LocalDate dateDeSeance, String motif, String commentaire, UUID auteur);

    /** Retire l'absence déclarée par une exclusion (sans effet si elle n'existe plus). */
    void retirer(UUID centre, UUID absence);
}
