package com.hemodialyse.backend.contexts.pilotagedirection.infrastructure.acl.absencespatients;

import com.hemodialyse.backend.contexts.absencespatients.publishedlanguage.AbsencesDuPilotage;
import com.hemodialyse.backend.contexts.pilotagedirection.application.AbsencesDesCentres;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** ACL : les absences des centres lues dans {@code absencespatients.v1} ({@link AbsencesDuPilotage}). Traduction pure, aucun SQL. */
public final class AbsencesParLesAbsencesDesPatients implements AbsencesDesCentres {

    private final AbsencesDuPilotage absences;

    public AbsencesParLesAbsencesDesPatients(AbsencesDuPilotage absences) {
        this.absences = absences;
    }

    @Override
    public List<CumulDuCentre> cumulParCentre(Collection<UUID> centres, LocalDate du, LocalDate au) {
        return absences.cumulParCentre(centres, du, au).stream()
                .map(g -> new CumulDuCentre(g.centre(), g.statut(), g.motif(), g.nombre(), g.montantHt(), g.montantTtc()))
                .toList();
    }

    @Override
    public List<AbsencesDuMois> mensuel(Collection<UUID> centres, LocalDate du, LocalDate au) {
        return absences.mensuel(centres, du, au).stream()
                .map(m -> new AbsencesDuMois(m.annee(), m.mois(), m.nombre(), m.montantHt()))
                .toList();
    }
}
