package com.hemodialyse.backend.contexts.seance.contrat;

import com.hemodialyse.backend.contexts.seance.publishedlanguage.FacturationDesSeancesPubliee;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.PlacesDesSeancesPubliees;

import java.util.UUID;

/** Le double en mémoire passe le même contrat que l'adapter réel. */
class LecturesPublieesDesSeancesEnMemoireContratTest extends LecturesPublieesDesSeancesContrat {

    private final LecturesPublieesDesSeancesEnMemoire lectures = new LecturesPublieesDesSeancesEnMemoire();

    @Override
    protected void poserSalle(UUID centre, UUID id, String code) {
        lectures.salle(centre, id, code);
    }

    @Override
    protected void poserCreneau(UUID centre, UUID id, String code, String libelle) {
        lectures.creneau(centre, id, code, libelle);
    }

    @Override
    protected void poserSeance(UUID centre, UUID id, String statut, UUID salle, UUID creneau) {
        lectures.seance(centre, id, statut, salle, creneau);
    }

    @Override
    protected FacturationDesSeancesPubliee facturation() {
        return lectures;
    }

    @Override
    protected PlacesDesSeancesPubliees places() {
        return lectures;
    }
}
