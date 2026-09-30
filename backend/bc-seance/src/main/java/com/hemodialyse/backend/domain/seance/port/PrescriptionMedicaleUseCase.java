package com.hemodialyse.backend.domain.seance.port;

import com.hemodialyse.backend.domain.seance.model.PrescriptionMedicale;
import com.hemodialyse.backend.domain.seance.model.UniteFrequence;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.domain.shared.vo.CenterId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface PrescriptionMedicaleUseCase {
    List<PrescriptionMedicale> listByPatient(CenterId centerId, UUID patientId, LocalDate from, LocalDate to);

    /**
     * Liste paginée des prescriptions du patient, de la plus récente à la plus ancienne (AGENTS.md §9).
     */
    PagedResult<PrescriptionMedicale> listPagedByPatient(CenterId centerId,
                                                         UUID patientId,
                                                         LocalDate from,
                                                         LocalDate to,
                                                         int page,
                                                         int size);

    PrescriptionMedicale save(CenterId centerId,
                              UUID patientId,
                              UUID prescriptionId,
                              LocalDate datePrescription,
                              UUID medecinId,
                              Integer qbCible,
                              Integer qdCible,
                              Integer ufMaxMl,
                              Integer dureeCibleMin,
                              BigDecimal poidsSecCibleKg,
                              String typeDialyseurPrescrit,
                              String anticoagTypePrescrit,
                              UUID epoArticleId,
                              Integer epoDoseUi,
                              String epoVoie,
                              Integer epoFrequenceValeur,
                              UniteFrequence epoFrequenceUnite,
                              UUID ferArticleId,
                              Integer ferDoseMg,
                              String ferVoie,
                              Integer ferFrequenceValeur,
                              UniteFrequence ferFrequenceUnite);

    void delete(CenterId centerId, UUID patientId, UUID prescriptionId);
}
