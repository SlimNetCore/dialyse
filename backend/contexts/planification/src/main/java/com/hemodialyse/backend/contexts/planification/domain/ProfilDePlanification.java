package com.hemodialyse.backend.contexts.planification.domain;

import com.hemodialyse.backend.contexts.planification.domain.Identifiants.InfirmierId;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Profil de planification d'un infirmier (RG-PLN-097) : taux d'activité (temps partiel, de {@value #TAUX_MIN} à 100 %) et
 * compétences particulières. Sans profil enregistré, l'infirmier travaille à temps plein sans compétence particulière. Les
 * compétences sont toujours dans l'ordre de l'énumération (KB-PLN-09 : plus d'ordre dépendant de la JVM).
 */
public record ProfilDePlanification(InfirmierId infirmier, int tauxActivite, Set<Competence> competences) {

    public static final int TAUX_MIN = 10;
    public static final int TAUX_PLEIN = 100;

    public ProfilDePlanification {
        if (infirmier == null) throw new SaisieInvalide("Infirmier requis");
        if (tauxActivite < TAUX_MIN || tauxActivite > TAUX_PLEIN) {
            throw new SaisieInvalide("Le taux d'activité doit être compris entre " + TAUX_MIN + " et " + TAUX_PLEIN + " %");
        }
        competences = competences == null || competences.isEmpty() ? Set.of()
                : Collections.unmodifiableSet(EnumSet.copyOf(competences));
    }

    public static ProfilDePlanification parDefaut(InfirmierId infirmier) {
        return new ProfilDePlanification(infirmier, TAUX_PLEIN, Set.of());
    }
}
