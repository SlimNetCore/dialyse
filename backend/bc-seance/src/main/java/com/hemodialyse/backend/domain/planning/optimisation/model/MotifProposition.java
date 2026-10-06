package com.hemodialyse.backend.domain.planning.optimisation.model;

import com.hemodialyse.backend.domain.planning.optimisation.model.ResultatOptimisation.Indicateurs;

import java.util.Arrays;
import java.util.Optional;

/**
 * Raison de signaler une proposition calculée par la replanification automatique nocturne. Chaque motif sait, pour le
 * périmètre qu'il couvre, mesurer ce que la proposition apporte ; une valeur nulle ne mérite pas de notification.
 */
public enum MotifProposition {

    /**
     * Couverture : vacations en sous-effectif que la proposition pourvoit, plus celles qui restent sans infirmier.
     */
    SOUS_EFFECTIF(PerimetreOptimisation.COUVERTURE) {
        @Override
        public int valeur(ResultatOptimisation resultat) {
            int nouvelles = (int) resultat.vacations().stream().filter(v -> !v.existante()).count();
            int manquantes = resultat.manques().stream().mapToInt(ResultatOptimisation.VacationNonPourvue::manque).sum();
            return nouvelles + manquantes;
        }
    },

    /**
     * Maintenance : séances à déplacer temporairement ou sans solution.
     */
    MAINTENANCE(PerimetreOptimisation.MAINTENANCE) {
        @Override
        public int valeur(ResultatOptimisation resultat) {
            return resultat.temporaires().size() + resultat.seancesSansSolution().size();
        }
    },

    /**
     * Placement des patients : vacations d'infirmiers économisées (ou patients en attente enfin placés).
     */
    GAIN(PerimetreOptimisation.PATIENTS) {
        @Override
        public int valeur(ResultatOptimisation resultat) {
            Indicateurs avant = resultat.avant();
            Indicateurs apres = resultat.apres();
            if (avant == null || apres == null) return 0;
            int economie = avant.vacationsRequises() - apres.vacationsRequises();
            int places = avant.patientsNonPlaces() - apres.patientsNonPlaces();
            return Math.max(0, economie) + Math.max(0, places);
        }
    };

    private final PerimetreOptimisation perimetre;

    MotifProposition(PerimetreOptimisation perimetre) {
        this.perimetre = perimetre;
    }

    public PerimetreOptimisation perimetre() {
        return perimetre;
    }

    /**
     * Intérêt de la proposition pour ce motif (0 : rien à signaler).
     */
    public abstract int valeur(ResultatOptimisation resultat);

    public static Optional<MotifProposition> pour(PerimetreOptimisation perimetre) {
        return Arrays.stream(values()).filter(m -> m.perimetre == perimetre).findFirst();
    }
}
