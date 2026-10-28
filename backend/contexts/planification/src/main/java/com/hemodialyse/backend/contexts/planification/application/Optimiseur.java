package com.hemodialyse.backend.contexts.planification.application;

import com.hemodialyse.backend.contexts.planification.domain.DonneesDOptimisation;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.ExecutionId;
import com.hemodialyse.backend.contexts.planification.domain.ParametresDOptimisation;
import com.hemodialyse.backend.contexts.planification.domain.ResultatDOptimisation;

/**
 * Port sortant : le solveur (ACL vers Timefold, ADR 0050). Il résout les données d'une optimisation EN DEHORS de toute transaction
 * (calcul asynchrone, borné par {@code dureeMaxSecondes}) et répond par l'écouteur. Les tests du domaine et de l'application
 * l'injectent sous forme d'une fausse implémentation déterministe : le solveur réel dépend du temps (RG-PLN-090).
 */
public interface Optimiseur {

    /** Lance le calcul et rend la main immédiatement ; l'écouteur reçoit la progression puis exactement un résultat ou un échec. */
    void demarrer(ExecutionId execution, DonneesDOptimisation donnees, ParametresDOptimisation parametres, Ecouteur ecouteur);

    /** Demande l'arrêt anticipé (la meilleure solution est conservée) ; faux si ce calcul n'est pas connu du solveur. */
    boolean arreter(ExecutionId execution);

    interface Ecouteur {
        void progression(String phase, String score);

        void termine(ResultatDOptimisation resultat, String score);

        void echec(String message);
    }
}
