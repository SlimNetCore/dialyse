package com.hemodialyse.backend.infrastructure.acl.facturation;

import com.hemodialyse.backend.contexts.facturation.publishedlanguage.SeancesDesFacturesFournies;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.SeancesDesFacturesPubliees;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Fournisseur de la Facturation (ADR 0097, SE7c2) : les comptages de séances de la synthèse et du tableau de bord passent par
 * {@link SeancesDesFacturesPubliees}, qui garde les DEUX définitions de « facturée » (KB-SEA-75). Logé dans la plateforme (cycle Maven).
 * Délégation pure, lecture seule.
 */
@Component
public class SeancesDesFacturesParLaSeance implements SeancesDesFacturesFournies {

    private final SeancesDesFacturesPubliees seances;

    public SeancesDesFacturesParLaSeance(SeancesDesFacturesPubliees seances) {
        this.seances = seances;
    }

    @Override
    public Map<UUID, Long> nombreParFacture(UUID centre, Collection<UUID> factures) {
        return seances.nombreParFacture(centre, factures);
    }

    @Override
    public long nombreDeSeancesFactureesEntre(UUID centre, LocalDate debut, LocalDate fin) {
        return seances.nombreDeSeancesFactureesEntre(centre, debut, fin);
    }
}
