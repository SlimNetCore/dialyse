package com.hemodialyse.backend.contexts.absencespatients.contrat;

import com.hemodialyse.backend.contexts.absencespatients.doubles.MoisAbsencesEnMemoire;
import com.hemodialyse.backend.contexts.absencespatients.domain.MoisAbsencesRepository;

/**
 * Le double en mémoire passe le contrat partagé des mois d'absences.
 */
class MoisAbsencesEnMemoireContratTest extends MoisAbsencesContrat {

    private final MoisAbsencesEnMemoire memoire = new MoisAbsencesEnMemoire();

    @Override
    protected MoisAbsencesRepository mois() {
        return memoire;
    }
}
