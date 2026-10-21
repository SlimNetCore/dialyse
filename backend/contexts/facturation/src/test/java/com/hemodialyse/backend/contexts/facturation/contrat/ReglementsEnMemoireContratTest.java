package com.hemodialyse.backend.contexts.facturation.contrat;

import com.hemodialyse.backend.contexts.facturation.application.reglement.SituationsDesFactures;
import com.hemodialyse.backend.contexts.facturation.doubles.ReglementsEnMemoire;
import com.hemodialyse.backend.contexts.facturation.domain.CentreId;
import com.hemodialyse.backend.contexts.facturation.domain.reglement.Reglements;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Le double en mémoire passe le contrat partagé des règlements et de leur lecture.
 */
class ReglementsEnMemoireContratTest extends ReglementsContrat {

    private final ReglementsEnMemoire memoire = new ReglementsEnMemoire();

    @Override
    protected Reglements reglements() {
        return memoire;
    }

    @Override
    protected SituationsDesFactures situations() {
        return memoire;
    }

    @Override
    protected void poserLaFacture(CentreId centre, UUID id, String numero, LocalDate dateFacturation, String total) {
        memoire.poserLaFacture(centre, id, numero, dateFacturation, new BigDecimal(total));
    }
}
