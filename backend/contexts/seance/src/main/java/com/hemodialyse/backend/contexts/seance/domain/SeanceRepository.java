package com.hemodialyse.backend.contexts.seance.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SeanceRepository {
    Optional<Seance> trouver(SeanceId id, CentreId centreId);

    void sauvegarder(Seance seance);

    void supprimer(SeanceId id, CentreId centreId);

    PagedResult<Seance> lister(CentreId centreId, int page, int size);

    PagedResult<Seance> listerDuJour(CentreId centreId, LocalDate date, int page, int size);

    List<Seance> listerParPatient(PatientId patientId, CentreId centreId);

    void verrouillerPourFacturation(CentreId centreId, java.time.LocalDate debut, java.time.LocalDate fin);
}