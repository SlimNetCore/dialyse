package com.hemodialyse.backend.contexts.stock.publishedlanguage;

import java.util.UUID;

/**
 * Port FOURNI AU Stock (ADR 0191, sur le modèle de {@link CentresFournis}) : le périmètre (salle, demi-journée) d'une séance pour les sorties
 * du Stock. La Séance dépend du Stock ; elle implémente ce port à partir de son langage publié. Séance inconnue ou d'un autre centre : le
 * périmètre « journée » (ni salle ni demi-journée). Lecture seule.
 */
public interface PerimetreDesSeancesFourni {

    PerimetreFourni perimetreDe(UUID centre, UUID seance);

    /** Salle (code, peut être nul) et demi-journée (peut être nulle). */
    record PerimetreFourni(String salle, DemiJourneeFournie demiJournee) {

        public static PerimetreFourni journee() {
            return new PerimetreFourni(null, null);
        }
    }

    enum DemiJourneeFournie {
        MATIN, APRES_MIDI
    }
}
