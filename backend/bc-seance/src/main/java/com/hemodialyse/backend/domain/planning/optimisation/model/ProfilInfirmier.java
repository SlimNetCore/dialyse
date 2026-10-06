package com.hemodialyse.backend.domain.planning.optimisation.model;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * Profil de planification d'un infirmier : taux d'activité (temps partiel) et compétences particulières. Sans profil
 * enregistré, un infirmier travaille à temps plein sans compétence particulière.
 *
 * @param tauxActivite pourcentage d'un temps plein, de {@value #TAUX_MIN} à 100
 */
public record ProfilInfirmier(UUID infirmierId, int tauxActivite, Set<CompetenceInfirmier> competences) {

    public static final int TAUX_MIN = 10;
    public static final int TAUX_PLEIN = 100;

    public ProfilInfirmier {
        if (infirmierId == null) throw new IllegalArgumentException("Infirmier requis");
        if (tauxActivite < TAUX_MIN || tauxActivite > TAUX_PLEIN) {
            throw new IllegalArgumentException("Le taux d'activité doit être compris entre " + TAUX_MIN + " et "
                    + TAUX_PLEIN + " %");
        }
        competences = competences == null || competences.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(competences));
    }

    public static ProfilInfirmier parDefaut(UUID infirmierId) {
        return new ProfilInfirmier(infirmierId, TAUX_PLEIN, Set.of());
    }

    /**
     * Heures hebdomadaires autorisées : temps plein du centre au prorata du taux d'activité (arrondi à l'heure).
     */
    public int quotaHeures(int heuresHebdoTempsPlein) {
        return (int) Math.round(heuresHebdoTempsPlein * tauxActivite / 100.0);
    }
}
