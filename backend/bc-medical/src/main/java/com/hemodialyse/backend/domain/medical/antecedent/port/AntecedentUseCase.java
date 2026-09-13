package com.hemodialyse.backend.domain.medical.antecedent.port;

import com.hemodialyse.backend.domain.medical.antecedent.aggregate.Antecedent;
import com.hemodialyse.backend.domain.medical.antecedent.valueobject.StatutClinique;
import com.hemodialyse.backend.domain.medical.antecedent.valueobject.TypeAntecedent;
import com.hemodialyse.backend.domain.medical.shared.valueobject.ConceptCode;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.domain.shared.vo.CenterId;

import java.time.LocalDate;
import java.util.UUID;

public interface AntecedentUseCase {

    PagedResult<Antecedent> listPagedByPatient(CenterId centerId, UUID patientId, int page, int size);

    Antecedent create(CenterId centerId, UUID patientId, TypeAntecedent type, ConceptCode diagnostic,
                      String libelleLibre, LocalDate dateDebut, LocalDate dateFin, String severite, String note);

    Antecedent update(CenterId centerId, UUID patientId, UUID antecedentId, ConceptCode diagnostic,
                      String libelleLibre, LocalDate dateDebut, LocalDate dateFin, StatutClinique statutClinique,
                      String severite, String note);

    Antecedent resoudre(CenterId centerId, UUID patientId, UUID antecedentId, LocalDate dateResolution);

    void delete(CenterId centerId, UUID patientId, UUID antecedentId);
}
