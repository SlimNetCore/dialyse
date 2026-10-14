package com.hemodialyse.backend.contexts.gmao.domain.intervention;

import com.hemodialyse.backend.contexts.gmao.domain.intervenant.NatureIntervenant;

import java.math.BigDecimal;

/**
 * Ce que l'intervenant de l'intervention apporte à la valorisation de son temps (R19) : sa nature et son tarif horaire
 * (tarif intervenant), lus à la clôture (KB-GMAO-46 : aucun instantané n'est pris à la planification). L'intervenant lui-même
 * reste un autre agrégat : l'intervention ne reçoit que ces deux valeurs. Le tarif est celui de la base, tel quel : absent,
 * nul ou même négatif (donnée ancienne), le temps n'est alors pas valorisé, sans erreur (KB-GMAO-42).
 */
public record ConditionsDeValorisation(NatureIntervenant nature, BigDecimal tarifHoraire) {

    boolean valorise() {
        return nature != null && tarifHoraire != null && tarifHoraire.signum() > 0;
    }

    boolean externe() {
        return nature == NatureIntervenant.EXTERNE;
    }
}
