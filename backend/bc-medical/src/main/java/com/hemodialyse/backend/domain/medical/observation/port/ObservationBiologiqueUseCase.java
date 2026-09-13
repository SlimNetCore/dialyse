package com.hemodialyse.backend.domain.medical.observation.port;

import com.hemodialyse.backend.domain.medical.observation.aggregate.ObservationBiologique;
import com.hemodialyse.backend.domain.medical.observation.valueobject.StatutObservation;
import com.hemodialyse.backend.domain.medical.observation.valueobject.ValeurMesuree;
import com.hemodialyse.backend.domain.medical.shared.valueobject.ConceptCode;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.domain.shared.vo.CenterId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ObservationBiologiqueUseCase {

    PagedResult<ObservationBiologique> listPagedByPatient(CenterId centerId, UUID patientId, String loincCode,
                                                          LocalDate from, LocalDate to, int page, int size);

    List<ObservationBiologique> listByDemandeExamen(CenterId centerId, UUID demandeExamenId);

    ObservationBiologique create(CenterId centerId, UUID patientId, UUID demandeExamenId, ConceptCode analyte,
                                 ValeurMesuree valeurNum, String valeurTexte, LocalDate datePrelevement,
                                 StatutObservation statut);

    ObservationBiologique corriger(CenterId centerId, UUID patientId, UUID observationId, ValeurMesuree valeurNum,
                                   String valeurTexte);

    void delete(CenterId centerId, UUID patientId, UUID observationId);
}
