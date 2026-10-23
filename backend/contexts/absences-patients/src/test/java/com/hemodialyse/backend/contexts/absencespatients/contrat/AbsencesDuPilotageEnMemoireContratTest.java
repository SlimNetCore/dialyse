package com.hemodialyse.backend.contexts.absencespatients.contrat;

import com.hemodialyse.backend.contexts.absencespatients.doubles.AbsencesDuPilotageEnMemoire;
import com.hemodialyse.backend.contexts.absencespatients.publishedlanguage.AbsencesDuPilotage;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Le double en mémoire passe le contrat partagé des lectures du pilotage.
 */
class AbsencesDuPilotageEnMemoireContratTest extends AbsencesDuPilotageContrat {

    private final AbsencesDuPilotageEnMemoire memoire = new AbsencesDuPilotageEnMemoire();

    @Override
    protected AbsencesDuPilotage pilotage() {
        return memoire;
    }

    @Override
    protected void poser(UUID centre, UUID patient, LocalDate date, String statut, String motif, BigDecimal ht, BigDecimal ttc) {
        memoire.poser(centre, patient, date, statut, motif, ht, ttc);
    }
}
