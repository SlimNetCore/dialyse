package com.hemodialyse.backend.contexts.comptabilite.application.journal;

import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;
import com.hemodialyse.backend.contexts.comptabilite.domain.journal.Journal;
import com.hemodialyse.backend.contexts.comptabilite.domain.journal.JournalRepository;

import java.util.List;

/**
 * Journaux effectifs d'un centre : ceux qu'il a enregistrés, sinon les cinq journaux par défaut (RG-CPT-009). Un centre ne
 * peut pas avoir « zéro journal » : supprimer le dernier fait réapparaître les journaux par défaut (KB-CPT-07).
 */
public final class JournauxDuCentreVus {

    private final JournalRepository journaux;

    public JournauxDuCentreVus(JournalRepository journaux) {
        this.journaux = journaux;
    }

    public List<Journal> effectifs(CentreId centre) {
        List<Journal> enregistres = journaux.duCentre(centre);
        return enregistres.isEmpty() ? Journal.parDefaut(centre) : enregistres;
    }

    public boolean aEnregistreSesJournaux(CentreId centre) {
        return !journaux.duCentre(centre).isEmpty();
    }

    /**
     * Première modification : les journaux par défaut deviennent ceux, enregistrés, du centre.
     */
    public void materialiser(CentreId centre) {
        if (!aEnregistreSesJournaux(centre)) {
            Journal.parDefaut(centre).forEach(journaux::enregistrer);
        }
    }
}
