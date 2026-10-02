package com.hemodialyse.backend.application.infirmier;

import com.hemodialyse.backend.domain.infirmier.model.AbsenceInfirmier;
import com.hemodialyse.backend.domain.infirmier.model.TypeAbsence;
import com.hemodialyse.backend.domain.infirmier.port.AbsenceInfirmierRepositoryPort;
import com.hemodialyse.backend.domain.infirmier.port.InfirmierRepositoryPort;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.domain.shared.exception.BusinessException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Déclaration des absences (congé, maladie, formation) des infirmiers du centre.
 */
@Service
public class AbsenceInfirmierService {

    private final AbsenceInfirmierRepositoryPort absences;
    private final InfirmierRepositoryPort infirmiers;

    public AbsenceInfirmierService(AbsenceInfirmierRepositoryPort absences, InfirmierRepositoryPort infirmiers) {
        this.absences = absences;
        this.infirmiers = infirmiers;
    }

    public PagedResult<AbsenceInfirmier> lister(UUID centerId, int page, int size) {
        return absences.findPaged(centerId, page, size);
    }

    public AbsenceInfirmier declarer(UUID centerId, UUID infirmierId, LocalDate debut, LocalDate fin, TypeAbsence type,
                                     String motif) {
        infirmiers.findById(centerId, infirmierId)
                .orElseThrow(() -> new BusinessException("INFIRMIER_INTROUVABLE", "Infirmier introuvable"));
        return absences.save(AbsenceInfirmier.creer(centerId, infirmierId, debut, fin, type, motif));
    }

    public void supprimer(UUID centerId, UUID id) {
        absences.findById(centerId, id)
                .orElseThrow(() -> new BusinessException("ABSENCE_INTROUVABLE", "Absence introuvable"));
        absences.delete(centerId, id);
    }
}
