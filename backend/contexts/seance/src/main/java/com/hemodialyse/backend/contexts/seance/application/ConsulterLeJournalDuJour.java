package com.hemodialyse.backend.contexts.seance.application;

import com.hemodialyse.backend.contexts.seance.domain.SaisieInvalide;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Journal du jour (GET /api/v1/seances/journal) : les patients qui ont une séance ce jour-là et les articles sortis du stock ce jour-là.
 * Lecture seule. Le centre doit être celui de la session (403, avant tout 400). Aucun filtre de statut (KB-SEA-61, reproduit).
 */
public class ConsulterLeJournalDuJour {

    private final SessionCourante session;
    private final PatientsDuJour patients;
    private final SortiesDuJour sorties;

    public ConsulterLeJournalDuJour(SessionCourante session, PatientsDuJour patients, SortiesDuJour sorties) {
        this.session = session;
        this.patients = patients;
        this.sorties = sorties;
    }

    public Journal executer(UUID centreDemande, LocalDate jour) {
        CentreId centre = session.centre(centreDemande);
        if (jour == null) throw new SaisieInvalide("Jour requis");
        return new Journal(jour, patients.deLaJournee(centre, jour), sorties.du(centre, jour));
    }

    public record Journal(LocalDate jour, List<PatientsDuJour.PatientDuJour> patients, List<SortiesDuJour.SortieDArticle> sorties) {
    }
}
