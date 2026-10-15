package com.hemodialyse.backend.contexts.comptabilite.doubles;

import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;
import com.hemodialyse.backend.contexts.comptabilite.domain.journal.CodeJournal;
import com.hemodialyse.backend.contexts.comptabilite.domain.journal.Journal;
import com.hemodialyse.backend.contexts.comptabilite.domain.journal.JournalRepository;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Double en mémoire des journaux d'un centre : même contrat que l'adaptateur JPA (voir les tests de contrat).
 */
public class JournauxEnMemoire implements JournalRepository {

    private final Map<Cle, Journal> journaux = new HashMap<>();

    @Override
    public List<Journal> duCentre(CentreId centre) {
        return journaux.values().stream().filter(j -> j.centre().equals(centre))
                .sorted(Comparator.comparing(j -> j.code().valeur())).toList();
    }

    @Override
    public Optional<Journal> trouver(CentreId centre, CodeJournal code) {
        return Optional.ofNullable(journaux.get(new Cle(centre, code)));
    }

    @Override
    public void enregistrer(Journal journal) {
        journaux.put(new Cle(journal.centre(), journal.code()), journal);
    }

    @Override
    public void supprimer(CentreId centre, CodeJournal code) {
        journaux.remove(new Cle(centre, code));
    }

    private record Cle(CentreId centre, CodeJournal code) {
    }
}
