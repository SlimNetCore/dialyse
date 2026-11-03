package com.hemodialyse.backend.infrastructure.acl.patientcouverture;

import com.hemodialyse.backend.application.notification.NotificationService;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.NotificationsDeLaFiche;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * ACL de la plateforme vers le port publié {@link NotificationsDeLaFiche} du contexte Patient et couverture (slice PC1) : les
 * diffusions {@code PATIENT_*} restent émises par le service de notification actuel.
 */
@Component
public class NotificationsDeLaFicheParLaPlateforme implements NotificationsDeLaFiche {

    private final NotificationService notifications;

    public NotificationsDeLaFicheParLaPlateforme(NotificationService notifications) {
        this.notifications = notifications;
    }

    @Override
    public void ficheCreee(UUID centre, UUID patient, String code, String nom, String prenom) {
        notifications.notifyPatientCreated(centre, patient, code, nom, prenom);
    }

    @Override
    public void ficheModifiee(UUID centre, UUID patient, String code, String nom, String prenom) {
        notifications.notifyPatientUpdated(centre, patient, code, nom, prenom);
    }
}
