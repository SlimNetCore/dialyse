package com.hemodialyse.backend.contexts.facturation.infrastructure.acl.comptabilite;

import com.hemodialyse.backend.contexts.comptabilite.publishedlanguage.ParametrageFiscal;
import com.hemodialyse.backend.contexts.facturation.application.simulation.TauxDeTvaApplicable;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.TauxDeTva;

import java.time.LocalDate;

/**
 * Le taux de TVA vient du langage publié de la Comptabilité, propriétaire des règles de TVA (ADR 0030) : celui de la règle
 * applicable aux prestations d'hémodialyse à la date, 0 sans règle (RG-FAC-002).
 */
public class TauxDeTvaComptabilite implements TauxDeTvaApplicable {

    static final String TYPE_DE_PRESTATION = "HEMODIALYSE";

    private final ParametrageFiscal parametrageFiscal;

    public TauxDeTvaComptabilite(ParametrageFiscal parametrageFiscal) {
        this.parametrageFiscal = parametrageFiscal;
    }

    @Override
    public TauxDeTva aLaDate(CentreId centre, LocalDate date) {
        return parametrageFiscal.regleApplicable(centre.valeur(), TYPE_DE_PRESTATION, date)
                .map(regle -> new TauxDeTva(regle.taux()))
                .orElse(TauxDeTva.AUCUN);
    }
}
