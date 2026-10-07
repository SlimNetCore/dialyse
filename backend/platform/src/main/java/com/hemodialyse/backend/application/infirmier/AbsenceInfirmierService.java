package com.hemodialyse.backend.application.infirmier;

import com.hemodialyse.backend.application.notification.NotificationService;
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
    private final NotificationService notifications;

    public AbsenceInfirmierService(AbsenceInfirmierRepositoryPort absences, InfirmierRepositoryPort infirmiers,
                                   NotificationService notifications) {
        this.absences = absences;
        this.infirmiers = infirmiers;
        this.notifications = notifications;
    }

    public PagedResult<AbsenceInfirmier> lister(UUID centerId, int page, int size) {
        return absences.findPaged(centerId, page, size);
    }

    /**
     * Déclare une absence saisie par l'administrateur (aucune alerte : il la connaît).
     */
    public AbsenceInfirmier declarer(UUID centerId, UUID infirmierId, LocalDate debut, LocalDate fin, TypeAbsence type,
                                     String motif) {
        return declarer(centerId, infirmierId, debut, fin, type, motif, true);
    }

    /**
     * Déclare une absence. Saisie par un autre profil que l'administrateur (secrétariat), elle lui est signalée : il doit
     * pouvoir organiser les remplacements.
     *
     * @param parAdministrateur la saisie vient de l'administrateur
     */
    public AbsenceInfirmier declarer(UUID centerId, UUID infirmierId, LocalDate debut, LocalDate fin, TypeAbsence type,
                                     String motif, boolean parAdministrateur) {
        var infirmier = infirmiers.findById(centerId, infirmierId)
                .orElseThrow(() -> new BusinessException("INFIRMIER_INTROUVABLE", "Infirmier introuvable"));
        AbsenceInfirmier absence = absences.save(AbsenceInfirmier.creer(centerId, infirmierId, debut, fin, type, motif));
        if (!parAdministrateur) {
            notifications.notifyAbsenceInfirmierEnregistree(centerId, infirmier.nomComplet(), debut, fin);
        }
        return absence;
    }

    public void supprimer(UUID centerId, UUID id) {
        absences.findById(centerId, id)
                .orElseThrow(() -> new BusinessException("ABSENCE_INTROUVABLE", "Absence introuvable"));
        absences.delete(centerId, id);
    }
}
