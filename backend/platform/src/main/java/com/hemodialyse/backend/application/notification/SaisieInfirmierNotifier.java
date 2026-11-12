package com.hemodialyse.backend.application.notification;

import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.FichesDesPatients;
import com.hemodialyse.backend.infrastructure.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Signale au médecin du centre la saisie {@code ABSENCE} d'une absence de patient (seule saisie encore émise par la plateforme, jusqu'à
 * PL13d) : retrouve l'identité du patient et l'auteur de la requête puis publie l'évènement {@code SAISIE_INFIRMIER}. Les saisies de la
 * Séance et de l'administration ({@code ANEMIE}) sont du catalogue de la Séance (PL13b, ADR 0154). Une notification ne fait jamais échouer
 * la saisie qu'elle accompagne.
 */
@Service
public class SaisieInfirmierNotifier {

    private static final Logger log = LoggerFactory.getLogger(SaisieInfirmierNotifier.class);

    private final NotificationService notifications;
    private final FichesDesPatients patients;

    public SaisieInfirmierNotifier(NotificationService notifications, FichesDesPatients patients) {
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
                var fiche = patients.noms(centerId, List.of(patientId)).get(patientId);
                if (fiche != null) {
                    nom = fiche.nom();
                    prenom = fiche.prenom();
                }
            }
            notifications.notifySaisieInfirmier(centerId, type, patientId, nom, prenom, CurrentUser.username(),
                    date == null ? null : date.toString());
        } catch (RuntimeException e) {
            log.warn("[NOTIF] Notification de la saisie {} impossible : {}", type, e.getMessage());
        }
    }
}
