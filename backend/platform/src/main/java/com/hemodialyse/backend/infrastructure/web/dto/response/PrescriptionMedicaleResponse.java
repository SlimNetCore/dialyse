package com.hemodialyse.backend.infrastructure.web.dto.response;

import com.hemodialyse.backend.domain.seance.model.PrescriptionMedicale;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Réponse « prescription médicale » : cibles de dialyse + traitement de l'anémie (EPO et fer injectable).
 */
public record PrescriptionMedicaleResponse(
        UUID id,
        UUID patientId,
        UUID centerId,
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
        String ferFrequence,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    public static PrescriptionMedicaleResponse from(PrescriptionMedicale p) {
        return new PrescriptionMedicaleResponse(
                p.getId(),
                p.getPatientId(),
                p.getCenterId(),
                p.getDatePrescription(),
                p.getMedecinId(),
                p.getQbCible(),
                p.getQdCible(),
                p.getUfMaxMl(),
                p.getDureeCibleMin(),
                p.getTypeDialyseurPrescrit(),
                p.getAnticoagTypePrescrit(),
                p.getEpoMolecule(),
                p.getEpoDoseUi(),
                p.getEpoVoie(),
                p.getEpoFrequence(),
                p.getFerMolecule(),
                p.getFerDoseMg(),
                p.getFerVoie(),
                p.getFerFrequence(),
                p.getCreatedAt(),
                p.getUpdatedAt()
        );
    }
}
