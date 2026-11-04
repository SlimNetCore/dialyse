package com.hemodialyse.backend.infrastructure.acl.patientcouverture;

import com.hemodialyse.backend.application.notification.NotificationService;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.NotificationsDeLaFiche;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.NotificationsDeLaPec;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.NotificationsDesAttestations;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * ACL de la plateforme vers les ports publiés {@link NotificationsDeLaFiche} (PC1), {@link NotificationsDeLaPec} (PC3) et
 * {@link NotificationsDesAttestations} (PC4) du contexte Patient et couverture : les diffusions {@code PATIENT_*}, {@code PEC_*}
 * et {@code ATTESTATION_*} restent émises par le service de notification actuel.
 */
@Component
public class NotificationsDeLaFicheParLaPlateforme implements NotificationsDeLaFiche, NotificationsDeLaPec,
        NotificationsDesAttestations {

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
    public void attestationCreee(UUID centre, UUID attestation, UUID patient) {
        notifications.notifyAttestationCreated(centre, attestation, patient);
    }

    @Override
    public void attestationSupprimee(UUID centre, UUID attestation, UUID patient) {
        notifications.notifyAttestationDeleted(centre, attestation, patient);
    }

    @Override
    public void pecSupprimee(UUID centre, UUID pec, UUID patient) {
        notifications.notifyPecDeleted(centre, pec, patient);
    }
}
