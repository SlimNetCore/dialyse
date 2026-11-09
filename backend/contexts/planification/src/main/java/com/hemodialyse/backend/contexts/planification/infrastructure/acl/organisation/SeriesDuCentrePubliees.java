package com.hemodialyse.backend.contexts.planification.infrastructure.acl.organisation;

import com.hemodialyse.backend.contexts.organisation.publishedlanguage.SallesEtCreneauxDuCentre;
import com.hemodialyse.backend.contexts.planification.application.SeriesDuCentre;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

/** Traducteur vers le langage publié d'Organisation (O4 lot D, ADR 0110) : le nombre de séries (créneaux) du centre. */
public class SeriesDuCentrePubliees implements SeriesDuCentre {

    private final SallesEtCreneauxDuCentre organisation;

    public SeriesDuCentrePubliees(SallesEtCreneauxDuCentre organisation) {
        this.organisation = organisation;
    }

    @Override
    public int nombre(CentreId centre) {
        return organisation.nombreDeCreneaux(centre.valeur());
    }
}
