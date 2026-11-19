package com.hemodialyse.backend.contexts.facturation.application.consultation;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Port sortant : les séances du centre rattachées à une facture, quel que soit leur statut, triées date puis identifiant (seance.v1). */
public interface SeancesDeLaFacture {

    List<SeanceRattachee> de(CentreId centre, UUID facture);

    /** @param forfaitSurcharge forfait imposé à la séance, {@code null} sinon */
    record SeanceRattachee(UUID seance, LocalDate date, String statut, UUID forfaitSurcharge) {
    }
}
