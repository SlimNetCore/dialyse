package com.hemodialyse.backend.domain.patient.port;

import com.hemodialyse.backend.domain.patient.model.MouvementLigne;
import com.hemodialyse.backend.domain.patient.model.MouvementPatient;
import com.hemodialyse.backend.domain.patient.model.TypeMouvementPatient;
import com.hemodialyse.backend.domain.shared.PagedResult;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Port Out — historique des mouvements de patients (écriture seule, jamais de modification ni de suppression).
 */
public interface MouvementPatientRepositoryPort {

    void save(MouvementPatient mouvement);

    /**
     * Mouvements d'un centre, du plus récent au plus ancien, filtrables par patient, type et période de date d'effet.
     * Tous les filtres sont facultatifs.
     */
    PagedResult<MouvementLigne> findPaged(UUID centerId, UUID patientId, TypeMouvementPatient type, LocalDate du,
                                          LocalDate au, int page, int size);
}
