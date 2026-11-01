package com.hemodialyse.backend.contexts.seance.application;

import com.hemodialyse.backend.contexts.seance.domain.DerogationId;
import com.hemodialyse.backend.contexts.seance.domain.DerogationRegularisation;
import com.hemodialyse.backend.contexts.seance.domain.SeanceId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

public interface DerogationRegularisationUseCases {
    DerogationId creer(CreerDerogationCommande commande);

    DerogationRegularisation consulter(DerogationId id, CentreId centreId);

    DerogationRegularisation consulterParSeance(SeanceId seanceId, CentreId centreId);

    record CreerDerogationCommande(SeanceId seanceId, CentreId centreId, String motif, String autorite) {
    }
}