package com.hemodialyse.backend.application.notification;

import com.hemodialyse.backend.domain.patient.port.PatientRepositoryPort;
import com.hemodialyse.backend.domain.patient.vo.PatientId;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.infrastructure.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Signale au médecin du centre une saisie de l'infirmier : retrouve l'identité du patient et l'auteur de la requête puis
 * publie l'évènement {@code SAISIE_INFIRMIER}. Une notification ne fait jamais échouer la saisie qu'elle accompagne.
 */
@Service
public class SaisieInfirmierNotifier {

    private static final Logger log = LoggerFactory.getLogger(SaisieInfirmierNotifier.class);

    private final NotificationService notifications;
    private final PatientRepositoryPort patients;

    public SaisieInfirmierNotifier(NotificationService notifications, PatientRepositoryPort patients) {
        this.notifications = notifications;
        this.patients = patients;
    }

    /**
     * @param type      l'une de {@link NotificationService#SAISIES_INFIRMIER}
     * @param patientId patient concerné (facultatif)
     * @param date      date de la séance ou de l'absence concernée (facultative)
     */
    public void saisie(UUID centerId, String type, UUID patientId, LocalDate date) {
        try {
            String nom = null;
            String prenom = null;
            if (patientId != null) {
                var patient = patients.findById(PatientId.of(patientId), CenterId.of(centerId)).orElse(null);
                if (patient != null) {
                    nom = patient.getNom();
                    prenom = patient.getPrenom();
                }
            }
            notifications.notifySaisieInfirmier(centerId, type, patientId, nom, prenom, CurrentUser.username(),
                    date == null ? null : date.toString());
        } catch (RuntimeException e) {
            log.warn("[NOTIF] Notification de la saisie {} impossible : {}", type, e.getMessage());
        }
    }
}
