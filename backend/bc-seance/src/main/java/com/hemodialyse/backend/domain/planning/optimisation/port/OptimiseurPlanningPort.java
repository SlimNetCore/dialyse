package com.hemodialyse.backend.domain.planning.optimisation.port;

import com.hemodialyse.backend.domain.planning.optimisation.model.DonneesOptimisation;
import com.hemodialyse.backend.domain.planning.optimisation.model.ParametresOptimisation;
import com.hemodialyse.backend.domain.planning.optimisation.model.ResultatOptimisation;

import java.util.UUID;

/**
 * Port de sortie : moteur d'optimisation du planning. Le calcul est asynchrone ; l'écouteur est notifié depuis le
 * thread du moteur. Le moteur ne lit ni n'écrit rien : il reçoit les données et renvoie une proposition.
 */
public interface OptimiseurPlanningPort {

    /**
     * Lance le calcul d'une exécution ; exactement l'un de {@link Ecouteur#termine} ou {@link Ecouteur#echec} est appelé.
     */
    void demarrer(UUID runId, DonneesOptimisation donnees, ParametresOptimisation parametres, Ecouteur ecouteur);

    /**
     * Demande l'arrêt anticipé : la meilleure solution trouvée est conservée et remise à l'écouteur.
     *
     * @return vrai si un calcul était en cours pour cette exécution
     */
    boolean arreter(UUID runId);

    interface Ecouteur {
        /**
         * @param phase PATIENTS ou INFIRMIERS
         * @param score score de la meilleure solution actuelle
         */
        void progression(String phase, String score);

        void termine(ResultatOptimisation resultat, String score);

        void echec(String message);
    }
}
