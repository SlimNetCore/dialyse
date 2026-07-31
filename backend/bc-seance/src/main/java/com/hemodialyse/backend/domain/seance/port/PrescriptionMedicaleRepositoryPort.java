package com.hemodialyse.backend.domain.seance.port;

import com.hemodialyse.backend.domain.seance.model.PrescriptionMedicale;
import com.hemodialyse.backend.domain.shared.vo.CenterId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface PrescriptionMedicaleRepositoryPort {
    List<PrescriptionMedicale> findByPatientId(UUID patientId, CenterId centerId, LocalDate from, LocalDate to);

    PrescriptionMedicale save(PrescriptionMedicale prescription);

    void deleteById(UUID prescriptionId, UUID patientId, CenterId centerId);
}
