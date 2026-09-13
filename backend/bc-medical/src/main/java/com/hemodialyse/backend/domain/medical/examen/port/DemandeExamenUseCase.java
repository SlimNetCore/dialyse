package com.hemodialyse.backend.domain.medical.examen.port;

import com.hemodialyse.backend.domain.medical.examen.aggregate.DemandeExamen;
import com.hemodialyse.backend.domain.medical.examen.entity.LigneDemandeExamen;
import com.hemodialyse.backend.domain.medical.examen.valueobject.CategorieExamen;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.domain.shared.vo.CenterId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface DemandeExamenUseCase {

    PagedResult<DemandeExamen> listPagedByPatient(CenterId centerId, UUID patientId, int page, int size);

    DemandeExamen create(CenterId centerId, UUID patientId, String prescripteurId, LocalDate dateDemande,
                         CategorieExamen categorie, boolean urgent, String motif, List<LigneDemandeExamen> lignes);

    DemandeExamen preleve(CenterId centerId, UUID patientId, UUID demandeId);

    DemandeExamen marquerResultatDisponible(CenterId centerId, UUID patientId, UUID demandeId);

    DemandeExamen valider(CenterId centerId, UUID patientId, UUID demandeId, String conclusion);

    DemandeExamen annuler(CenterId centerId, UUID patientId, UUID demandeId);
}
