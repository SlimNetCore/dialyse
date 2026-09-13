package com.hemodialyse.backend.domain.medical.anemie.port;

import com.hemodialyse.backend.domain.medical.anemie.aggregate.AdministrationTraitement;
import com.hemodialyse.backend.domain.medical.anemie.valueobject.DoseAdministree;
import com.hemodialyse.backend.domain.medical.anemie.valueobject.TypeTraitementAnemie;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.domain.shared.vo.CenterId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface AdministrationTraitementUseCase {

    PagedResult<AdministrationTraitement> listPagedByPatient(CenterId centerId, UUID patientId, int page, int size);

    List<AdministrationTraitement> listByPatient(CenterId centerId, UUID patientId);

    AdministrationTraitement create(CenterId centerId, UUID patientId, UUID prescriptionMedicaleId,
                                    TypeTraitementAnemie typeTraitement, String molecule, DoseAdministree dose,
                                    String voie, LocalDate dateAdministration, UUID seanceId, String administrePar,
                                    boolean administree, String motifNonAdministration);
}
