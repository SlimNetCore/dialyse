package com.hemodialyse.backend.domain.absence.port;

import com.hemodialyse.backend.domain.absence.model.AbsenceFiltre;
import com.hemodialyse.backend.domain.absence.model.AbsenceLigne;
import com.hemodialyse.backend.domain.absence.model.AbsencePatient;
import com.hemodialyse.backend.domain.shared.PagedResult;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Port de sortie : persistance des absences de patients (toujours scopée par centre).
 */
public interface AbsencePatientRepositoryPort {

    AbsencePatient save(AbsencePatient absence);

    Optional<AbsencePatient> findById(UUID centerId, UUID id);

    Optional<AbsencePatient> findByPatientAndDate(UUID centerId, UUID patientId, LocalDate dateSeance);

    PagedResult<AbsenceLigne> findPaged(UUID centerId, AbsenceFiltre filtre, int page, int size);

    long countAQualifier(UUID centerId);

    long countEnRetard(UUID centerId, LocalDate limite);
}
