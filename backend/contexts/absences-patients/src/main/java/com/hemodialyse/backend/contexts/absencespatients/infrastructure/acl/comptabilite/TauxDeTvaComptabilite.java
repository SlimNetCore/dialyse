package com.hemodialyse.backend.contexts.absencespatients.infrastructure.acl.comptabilite;

import com.hemodialyse.backend.contexts.absencespatients.application.TauxDeTva;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.comptabilite.publishedlanguage.ParametrageFiscal;
import com.hemodialyse.backend.contexts.comptabilite.publishedlanguage.ParametrageFiscal.RegleTvaApplicable;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Taux de TVA de l'hémodialyse lu par le langage publié de la Comptabilité ({@code ParametrageFiscal}) : le type de prestation
 * {@code HEMODIALYSE} exact, la règle de la date (bornes incluses) du centre. Une règle exonérée se lit 0 % ici alors que la
 * Facturation applique son taux (KB-CPT-19, reproduit) ; sans règle, 0 %.
 */
public class TauxDeTvaComptabilite implements TauxDeTva {

    static final String TYPE_DE_PRESTATION = "HEMODIALYSE";

    private final ParametrageFiscal fiscal;

    public TauxDeTvaComptabilite(ParametrageFiscal fiscal) {
        this.fiscal = fiscal;
    }

    @Override
    public BigDecimal pour(CentreId centre, LocalDate date) {
        return fiscal.regleApplicable(centre.valeur(), TYPE_DE_PRESTATION, date)
                .filter(regle -> !regle.exonere())
                .map(RegleTvaApplicable::taux)
                .orElse(BigDecimal.ZERO);
    }
}
