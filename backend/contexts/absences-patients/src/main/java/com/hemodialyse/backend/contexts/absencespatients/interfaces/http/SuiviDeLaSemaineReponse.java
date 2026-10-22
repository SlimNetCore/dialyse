package com.hemodialyse.backend.contexts.absencespatients.interfaces.http;

import com.hemodialyse.backend.contexts.absencespatients.application.SuiviDeLaSemaine;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Suivi de la semaine : noms JSON inchangés.
 */
public record SuiviDeLaSemaineReponse(List<AbsenceDeLaSemaine> absences, List<SeanceRealisee> seancesRealisees) {

    public record AbsenceDeLaSemaine(UUID absenceId, UUID patientId, LocalDate dateSeance, String statut, String motif) {
    }

    public record SeanceRealisee(UUID patientId, LocalDate dateSeance) {
    }

    static SuiviDeLaSemaineReponse depuis(SuiviDeLaSemaine suivi) {
        return new SuiviDeLaSemaineReponse(
                suivi.absences().stream()
                        .map(a -> new AbsenceDeLaSemaine(a.id().valeur(), a.patient().valeur(), a.dateDeSeance(),
                                a.statut().name(), a.motif() == null ? null : a.motif().name()))
                        .toList(),
                suivi.seancesRealisees().stream()
                        .map(s -> new SeanceRealisee(s.patient().valeur(), s.date()))
                        .toList());
    }
}
