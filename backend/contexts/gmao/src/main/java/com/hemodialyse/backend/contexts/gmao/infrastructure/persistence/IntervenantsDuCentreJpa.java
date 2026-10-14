package com.hemodialyse.backend.contexts.gmao.infrastructure.persistence;

import com.hemodialyse.backend.contexts.gmao.application.intervention.IntervenantsDuCentre;
import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.intervenant.IntervenantId;
import com.hemodialyse.backend.contexts.gmao.domain.intervenant.NatureIntervenant;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.ConditionsDeValorisation;

import java.util.Optional;

/**
 * Adapter JPA du port {@link IntervenantsDuCentre} : lit la table des intervenants (même contexte) et rend le tarif tel que
 * stocké, sans passer par l'agrégat qui refuserait un tarif négatif ancien (KB-GMAO-42 : aucune ligne, sans erreur).
 */
public class IntervenantsDuCentreJpa implements IntervenantsDuCentre {

    private final IntervenantSpringData donnees;

    public IntervenantsDuCentreJpa(IntervenantSpringData donnees) {
        this.donnees = donnees;
    }

    @Override
    public Optional<ConditionsDeValorisation> conditionsDe(IntervenantId intervenant, CentreId centre) {
        return donnees.findById(intervenant.valeur())
                .filter(i -> i.getCentreId().equals(centre.valeur()))
                .map(i -> new ConditionsDeValorisation(NatureIntervenant.valueOf(i.getNature()), i.getTarifHoraire()));
    }
}
