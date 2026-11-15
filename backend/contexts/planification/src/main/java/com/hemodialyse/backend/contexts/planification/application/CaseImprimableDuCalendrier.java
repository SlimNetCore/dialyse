package com.hemodialyse.backend.contexts.planification.application;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Une ligne (salle x créneau d'une semaine) du planning calendaire figé d'une proposition, telle qu'elle s'imprime (DOC5c, ADR 0195) : les
 * sept en-têtes de colonne et les sept contenus de case, en texte prêt à imprimer ; une colonne absente de la semaine est nulle.
 */
public record CaseImprimableDuCalendrier(LocalDate semaineDebut, String salleNom, String creneauLibelle, int creneauOrdre,
                                         List<String> entetes, List<String> cellules) {

    public static final int NB_JOURS = 7;

    public CaseImprimableDuCalendrier {
        entetes = Collections.unmodifiableList(new ArrayList<>(entetes));
        cellules = Collections.unmodifiableList(new ArrayList<>(cellules));
        if (entetes.size() != NB_JOURS || cellules.size() != NB_JOURS) {
            throw new IllegalArgumentException("Une case imprimable porte exactement " + NB_JOURS + " en-têtes et " + NB_JOURS + " contenus");
        }
    }
}
