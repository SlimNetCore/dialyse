package com.hemodialyse.backend.domain.seance.port;

import com.hemodialyse.backend.domain.seance.model.PrescriptionMedicale;
import com.hemodialyse.backend.domain.shared.vo.CenterId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface PrescriptionMedicaleUseCase {
    List<PrescriptionMedicale> listByPatient(CenterId centerId, UUID patientId, LocalDate from, LocalDate to);

    PrescriptionMedicale save(CenterId centerId,
                              UUID patientId,
                              UUID prescriptionId,
                              LocalDate datePrescription,
                              UUID medecinId,
                              Integer qbCible,
                              Integer qdCible,
                              Integer ufMaxMl,
                              Integer dureeCibleMin,
                              String typeDialyseurPrescrit,
                              String anticoagTypePrescrit,
                              String epoMolecule,
                              Integer epoDoseUi,
                              String epoVoie,
                              String epoFrequence,
                              String ferMolecule,
                              Integer ferDoseMg,
                              String ferVoie,
                              String ferFrequence);

    void delete(CenterId centerId, UUID patientId, UUID prescriptionId);
}
