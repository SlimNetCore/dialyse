package com.hemodialyse.backend.contexts.comptabilite.application.tva;

import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;
import com.hemodialyse.backend.contexts.comptabilite.domain.tva.RegleTva;
import com.hemodialyse.backend.contexts.comptabilite.domain.tva.RegleTvaRepository;
import com.hemodialyse.backend.contexts.comptabilite.publishedlanguage.ParametrageFiscal;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Langage publié : la règle de TVA applicable à une date. Parmi les règles actives du type, celle qui a commencé le plus
 * récemment (les règles viennent de la plus récente à la plus ancienne) ; une règle terminée laisse donc revenir une règle
 * plus ancienne restée ouverte (KB-CPT-22, reproduit).
 */
public final class ConsulterLeParametrageFiscal implements ParametrageFiscal {

    private final RegleTvaRepository regles;

    public ConsulterLeParametrageFiscal(RegleTvaRepository regles) {
        this.regles = regles;
    }

    private static RegleTvaApplicable publiee(RegleTva r) {
        return new RegleTvaApplicable(r.typePrestation(), r.taux().valeur(), r.exonere(), r.debutValidite(), r.finValidite(),
                r.texteReference());
    }

    @Override
    public Optional<RegleTvaApplicable> regleApplicable(UUID centre, String typePrestation, LocalDate date) {
        return regles.duCentre(new CentreId(centre)).stream()
                .filter(r -> r.estDuType(typePrestation) && r.estApplicableLe(date))
                .findFirst().map(ConsulterLeParametrageFiscal::publiee);
    }
}
