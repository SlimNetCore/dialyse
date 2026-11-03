package com.hemodialyse.backend.infrastructure.acl.patientcouverture;

import com.hemodialyse.backend.application.notification.NotificationService;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.NotificationsDeLaFiche;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.NotificationsDeLaPec;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * ACL de la plateforme vers les ports publiés {@link NotificationsDeLaFiche} (PC1) et {@link NotificationsDeLaPec} (PC3) du
 * contexte Patient et couverture : les diffusions {@code PATIENT_*} et {@code PEC_*} restent émises par le service de notification
 * actuel.
 */
@Component
public class NotificationsDeLaFicheParLaPlateforme implements NotificationsDeLaFiche, NotificationsDeLaPec {

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

    @Override
    public void pecValidee(UUID centre, UUID pec, UUID patient, String nomDuPatient) {
        notifications.notifyPecValidated(centre, pec, patient, nomDuPatient);
    }

    @Override
    public void pecCloturee(UUID centre, UUID pec, UUID patient, String nomDuPatient) {
        notifications.notifyPecClosed(centre, pec, patient, nomDuPatient);
    }

    @Override
    public void pecSupprimee(UUID centre, UUID pec, UUID patient) {
        notifications.notifyPecDeleted(centre, pec, patient);
    }
}
