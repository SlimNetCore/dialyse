package com.hemodialyse.backend.contexts.seance.infrastructure.acl.facturation;

import com.hemodialyse.backend.contexts.facturation.publishedlanguage.SeancesDesFacturesFournies;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.SeancesDesFacturesPubliees;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Fournisseur de la Facturation (ADR 0097, SE7c2) : les comptages de séances de la synthèse et du tableau de bord passent par
 * {@link SeancesDesFacturesPubliees}, qui garde les DEUX définitions de « facturée » (KB-SEA-75). Hébergé par la Séance (cycle Maven, ADR 0191).
 * Délégation pure, lecture seule.
 */
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
    public Map<UUID, Long> nombreParFactureAuStatutFacturee(UUID centre, Collection<UUID> factures) {
        return seances.nombreParFactureAuStatutFacturee(centre, factures);
    }

    @Override
    public long nombreDeSeancesFactureesEntre(UUID centre, LocalDate debut, LocalDate fin) {
        return seances.nombreDeSeancesFactureesEntre(centre, debut, fin);
    }

    @Override
    public List<SeanceDeFactureFournie> seancesDeLaFacture(UUID centre, UUID facture) {
        return seances.seancesDeLaFacture(centre, facture).stream()
                .map(s -> new SeanceDeFactureFournie(s.id(), s.date(), s.statut(), s.forfaitSurcharge())).toList();
    }
}
