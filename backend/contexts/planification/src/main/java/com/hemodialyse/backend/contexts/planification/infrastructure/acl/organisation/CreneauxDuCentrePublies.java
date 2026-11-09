package com.hemodialyse.backend.contexts.planification.infrastructure.acl.organisation;

import com.hemodialyse.backend.contexts.organisation.publishedlanguage.SallesEtCreneauxDuCentre;
import com.hemodialyse.backend.contexts.planification.application.CreneauxDuCentre;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CreneauId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

/** Traducteur vers le langage publié d'Organisation (O4 lot D, ADR 0110) : l'appartenance d'un créneau à un centre. */
public class CreneauxDuCentrePublies implements CreneauxDuCentre {

    private final SallesEtCreneauxDuCentre organisation;

    public CreneauxDuCentrePublies(SallesEtCreneauxDuCentre organisation) {
        this.organisation = organisation;
    }

    @Override
    public boolean contient(CentreId centre, CreneauId creneau) {
        return organisation.contientLeCreneau(centre.valeur(), creneau.valeur());
    }
}
