package com.hemodialyse.backend.contexts.facturation.infrastructure.acl.seance;

import com.hemodialyse.backend.contexts.facturation.application.consultation.SeancesDeLaFacture;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.SeancesDesFacturesFournies;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.List;
import java.util.UUID;

/** Les séances d'une facture lues par la Séance ({@code seance.v1}, ADR 0097) via le langage fourni ; traduction pure, lecture seule. */
public class SeancesDeLaFacturePubliees implements SeancesDeLaFacture {

    private final SeancesDesFacturesFournies seances;

    public SeancesDeLaFacturePubliees(SeancesDesFacturesFournies seances) {
        this.seances = seances;
    }

    @Override
    public List<SeanceRattachee> de(CentreId centre, UUID facture) {
        return seances.seancesDeLaFacture(centre.valeur(), facture).stream()
                .map(s -> new SeanceRattachee(s.id(), s.date(), s.statut(), s.forfaitSurcharge())).toList();
    }
}
