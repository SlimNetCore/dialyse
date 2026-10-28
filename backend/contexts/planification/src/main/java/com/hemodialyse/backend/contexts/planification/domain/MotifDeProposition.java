package com.hemodialyse.backend.contexts.planification.domain;

import com.hemodialyse.backend.contexts.planification.domain.ResultatDOptimisation.Indicateurs;

import java.util.Arrays;
import java.util.Optional;

/**
 * Raison de signaler une proposition calculée par la replanification nocturne (P23, RG-PLN-100). Chaque motif sait, pour le périmètre
 * qu'il couvre, mesurer ce que la proposition apporte ; une valeur nulle ne mérite pas de notification.
 */
public enum MotifDeProposition {

    /** Couverture : vacations en sous-effectif que la proposition pourvoit, plus celles qui restent sans infirmier. */
    SOUS_EFFECTIF(Perimetre.COUVERTURE) {
        @Override
        public int valeur(ResultatDOptimisation resultat) {
            int nouvelles = (int) resultat.vacations().stream().filter(v -> !v.existante()).count();
            int manquantes = resultat.manques().stream().mapToInt(ResultatDOptimisation.VacationNonPourvue::manque).sum();
            return nouvelles + manquantes;
        }
    },

    /** Maintenance : séances à déplacer temporairement ou sans solution. */
    MAINTENANCE(Perimetre.MAINTENANCE) {
        @Override
        public int valeur(ResultatDOptimisation resultat) {
            return resultat.temporaires().size() + resultat.seancesSansSolution().size();
        }
    },

    /** Placement des patients : vacations d'infirmiers économisées (ou patients en attente enfin placés). */
    GAIN(Perimetre.PATIENTS) {
        @Override
        public int valeur(ResultatDOptimisation resultat) {
            Indicateurs avant = resultat.avant();
            Indicateurs apres = resultat.apres();
            if (avant == null || apres == null) return 0;
            int economie = avant.vacationsRequises() - apres.vacationsRequises();
            int places = avant.patientsNonPlaces() - apres.patientsNonPlaces();
            return Math.max(0, economie) + Math.max(0, places);
        }
    };

    private final Perimetre perimetre;

    MotifDeProposition(Perimetre perimetre) {
        this.perimetre = perimetre;
    }

    public Perimetre perimetre() {
        return perimetre;
    }

    /** Intérêt de la proposition pour ce motif (0 : rien à signaler). */
    public abstract int valeur(ResultatDOptimisation resultat);

    public static Optional<MotifDeProposition> pour(Perimetre perimetre) {
        return Arrays.stream(values()).filter(m -> m.perimetre == perimetre).findFirst();
    }
}
