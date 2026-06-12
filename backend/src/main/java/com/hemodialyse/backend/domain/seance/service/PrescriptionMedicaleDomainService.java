package com.hemodialyse.backend.domain.seance.service;

import com.hemodialyse.backend.domain.seance.model.PrescriptionMedicale;
import com.hemodialyse.backend.domain.seance.port.PrescriptionMedicaleRepositoryPort;
import com.hemodialyse.backend.domain.seance.port.PrescriptionMedicaleUseCase;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class PrescriptionMedicaleDomainService implements PrescriptionMedicaleUseCase {

    private final PrescriptionMedicaleRepositoryPort repository;

    public PrescriptionMedicaleDomainService(PrescriptionMedicaleRepositoryPort repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PrescriptionMedicale> listByPatient(CenterId centerId, UUID patientId, LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("La date from doit être <= à la date to");
        }
        return repository.findByPatientId(patientId, centerId, from, to);
    }

    @Override
    public PrescriptionMedicale save(CenterId centerId,
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
                                     String ferFrequence) {
        PrescriptionMedicale prescription = new PrescriptionMedicale();
        prescription.setId(prescriptionId != null ? prescriptionId : UUID.randomUUID());
        prescription.setPatientId(patientId);
        prescription.setCenterId(centerId.value());
        prescription.setDatePrescription(datePrescription != null ? datePrescription : LocalDate.now());
        prescription.setMedecinId(medecinId);
        prescription.setQbCible(qbCible);
        prescription.setQdCible(qdCible);
        prescription.setUfMaxMl(ufMaxMl);
        prescription.setDureeCibleMin(dureeCibleMin);
        prescription.setTypeDialyseurPrescrit(typeDialyseurPrescrit);
        prescription.setAnticoagTypePrescrit(anticoagTypePrescrit);
        prescription.setEpoMolecule(epoMolecule);
        prescription.setEpoDoseUi(epoDoseUi);
        prescription.setEpoVoie(epoVoie);
        prescription.setEpoFrequence(epoFrequence);
        prescription.setFerMolecule(ferMolecule);
        prescription.setFerDoseMg(ferDoseMg);
        prescription.setFerVoie(ferVoie);
        prescription.setFerFrequence(ferFrequence);
        OffsetDateTime now = OffsetDateTime.now();
        prescription.setCreatedAt(now);
        prescription.setUpdatedAt(now);
        return repository.save(prescription);
    }

    @Override
    public void delete(CenterId centerId, UUID patientId, UUID prescriptionId) {
        repository.deleteById(prescriptionId, patientId, centerId);
    }
}
