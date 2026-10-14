package com.hemodialyse.backend.contexts.gmao.application.intervention;

import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.intervenant.IntervenantId;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.ConditionsDeValorisation;

import java.util.Optional;

/**
 * Port sortant : ce que l'intervention sait de ses intervenants (même contexte GMAO, agrégat {@code Intervenant}) : leur
 * nature et leur tarif horaire, tels que lus à l'instant de la question (KB-GMAO-46). Vide si l'intervenant est inconnu ou
 * d'un autre centre (indistinguables) ; un intervenant désactivé est rendu (KB-GMAO-25). Le tarif est rendu tel que stocké,
 * sans validation : une valeur ancienne négative ne doit pas bloquer une clôture.
 */
public interface IntervenantsDuCentre {

    Optional<ConditionsDeValorisation> conditionsDe(IntervenantId intervenant, CentreId centre);
}
