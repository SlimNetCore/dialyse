package com.hemodialyse.backend.application.patient;

import com.hemodialyse.backend.domain.patient.model.MouvementLigne;
import com.hemodialyse.backend.domain.patient.model.TypeMouvementPatient;
import com.hemodialyse.backend.domain.patient.port.MouvementPatientRepositoryPort;
import com.hemodialyse.backend.domain.shared.PagedResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Consultation de l'historique des mouvements de patients d'un centre (liste paginée).
 */
@Service
@Transactional(readOnly = true)
public class MouvementPatientQueryService {

    static final int TAILLE_MAX = 100;

    private final MouvementPatientRepositoryPort mouvements;

    public MouvementPatientQueryService(MouvementPatientRepositoryPort mouvements) {
        this.mouvements = mouvements;
    }

    public PagedResult<MouvementLigne> lister(UUID centerId, UUID patientId, TypeMouvementPatient type, LocalDate du,
                                              LocalDate au, int page, int size) {
        return mouvements.findPaged(centerId, patientId, type, du, au, Math.max(0, page),
                Math.min(TAILLE_MAX, Math.max(1, size)));
    }
}
