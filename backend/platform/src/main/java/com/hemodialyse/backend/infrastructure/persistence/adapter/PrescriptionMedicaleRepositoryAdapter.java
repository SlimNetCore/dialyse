package com.hemodialyse.backend.infrastructure.persistence.adapter;

import com.hemodialyse.backend.domain.seance.model.PrescriptionMedicale;
import com.hemodialyse.backend.domain.seance.port.PrescriptionMedicaleRepositoryPort;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.infrastructure.persistence.entity.PrescriptionMedicaleJpaEntity;
import com.hemodialyse.backend.infrastructure.persistence.repository.PrescriptionMedicaleJpaRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Component
public class PrescriptionMedicaleRepositoryAdapter implements PrescriptionMedicaleRepositoryPort {

    private final PrescriptionMedicaleJpaRepository jpa;

    public PrescriptionMedicaleRepositoryAdapter(PrescriptionMedicaleJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public List<PrescriptionMedicale> findByPatientId(UUID patientId, CenterId centerId, LocalDate from, LocalDate to) {
        List<PrescriptionMedicaleJpaEntity> rows;
        if (from != null && to != null) {
            rows = jpa.findByPatientIdAndCenterIdAndDatePrescriptionBetweenOrderByDatePrescriptionDesc(
                    patientId, centerId.value(), from, to);
        } else if (from != null) {
            rows = jpa.findByPatientIdAndCenterIdAndDatePrescriptionGreaterThanEqualOrderByDatePrescriptionDesc(
                    patientId, centerId.value(), from);
        } else if (to != null) {
            rows = jpa.findByPatientIdAndCenterIdAndDatePrescriptionLessThanEqualOrderByDatePrescriptionDesc(
                    patientId, centerId.value(), to);
        } else {
            rows = jpa.findByPatientIdAndCenterIdOrderByDatePrescriptionDesc(patientId, centerId.value());
        }
        return rows.stream().map(this::toDomain).toList();
    }

    @Override
    public PrescriptionMedicale save(PrescriptionMedicale prescription) {
        return toDomain(jpa.save(toJpa(prescription)));
    }

    private PrescriptionMedicale toDomain(PrescriptionMedicaleJpaEntity e) {
        PrescriptionMedicale p = new PrescriptionMedicale();
        p.setId(e.getId());
        p.setPatientId(e.getPatientId());
        p.setCenterId(e.getCenterId());
        p.setDatePrescription(e.getDatePrescription());
        p.setMedecinId(e.getMedecinId());
        p.setQbCible(e.getQbCible());
        p.setQdCible(e.getQdCible());
        p.setUfMaxMl(e.getUfMaxMl());
        p.setDureeCibleMin(e.getDureeCibleMin());
        p.setTypeDialyseurPrescrit(e.getTypeDialyseurPrescrit());
        p.setAnticoagTypePrescrit(e.getAnticoagTypePrescrit());
        p.setEpoMolecule(e.getEpoMolecule());
        p.setEpoDoseUi(e.getEpoDoseUi());
        p.setEpoVoie(e.getEpoVoie());
        p.setEpoFrequence(e.getEpoFrequence());
        p.setFerMolecule(e.getFerMolecule());
        p.setFerDoseMg(e.getFerDoseMg());
        p.setFerVoie(e.getFerVoie());
        p.setFerFrequence(e.getFerFrequence());
        p.setCreatedAt(e.getCreatedAt());
        p.setUpdatedAt(e.getUpdatedAt());
        return p;
    }

    private PrescriptionMedicaleJpaEntity toJpa(PrescriptionMedicale p) {
        PrescriptionMedicaleJpaEntity e = new PrescriptionMedicaleJpaEntity();
        e.setId(p.getId());
        e.setPatientId(p.getPatientId());
        e.setCenterId(p.getCenterId());
        e.setDatePrescription(p.getDatePrescription());
        e.setMedecinId(p.getMedecinId());
        e.setQbCible(p.getQbCible());
        e.setQdCible(p.getQdCible());
        e.setUfMaxMl(p.getUfMaxMl());
        e.setDureeCibleMin(p.getDureeCibleMin());
        e.setTypeDialyseurPrescrit(p.getTypeDialyseurPrescrit());
        e.setAnticoagTypePrescrit(p.getAnticoagTypePrescrit());
        e.setEpoMolecule(p.getEpoMolecule());
        e.setEpoDoseUi(p.getEpoDoseUi());
        e.setEpoVoie(p.getEpoVoie());
        e.setEpoFrequence(p.getEpoFrequence());
        e.setFerMolecule(p.getFerMolecule());
        e.setFerDoseMg(p.getFerDoseMg());
        e.setFerVoie(p.getFerVoie());
        e.setFerFrequence(p.getFerFrequence());
        e.setCreatedAt(p.getCreatedAt());
        e.setUpdatedAt(p.getUpdatedAt());
        return e;
    }

    @Override
    public void deleteById(UUID prescriptionId, UUID patientId, CenterId centerId) {
        jpa.deleteByIdAndPatientIdAndCenterId(prescriptionId, patientId, centerId.value());
    }
}
