package com.hemodialyse.backend.infrastructure.acl.planification;

import com.hemodialyse.backend.application.notification.NotificationService;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.NotificationsDesAbsencesDInfirmiers;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Fournit au contexte Planification son port consommé {@link NotificationsDesAbsencesDInfirmiers} (ACL vers le centre de
 * notifications legacy, retirée quand les notifications seront un contexte) : journal durable et diffusion temps réel du centre, qui
 * rafraîchit les écrans de planification.
 */
@Component
public class NotificationsDesAbsencesDInfirmiersLegacy implements NotificationsDesAbsencesDInfirmiers {

    private final NotificationService notifications;

    public NotificationsDesAbsencesDInfirmiersLegacy(NotificationService notifications) {
        this.notifications = notifications;
    }

    @Override
    public void absenceEnregistree(UUID centre, String infirmier, LocalDate debut, LocalDate fin, boolean prevenirAdministrateur) {
        notifications.notifyAbsenceInfirmierEnregistree(centre, infirmier, debut, fin, prevenirAdministrateur);
    }

    @Override
    public void absenceDeclareeParLInfirmier(UUID centre, String infirmier, LocalDate debut, LocalDate fin) {
        notifications.notifyAbsenceInfirmierDeclaree(centre, infirmier, debut, fin);
    }

    @Override
    public void absenceRetireeParLAdministration(UUID centre, String infirmier, LocalDate debut, LocalDate fin,
                                                 boolean prevenirAdministrateur) {
        notifications.notifyAbsenceInfirmierRetiree(centre, infirmier, debut, fin, prevenirAdministrateur ? "ADMIN,MEDECIN" : "MEDECIN");
    }

    @Override
    public void absenceRetireeParLInfirmier(UUID centre, String infirmier, LocalDate debut, LocalDate fin) {
        notifications.notifyAbsenceInfirmierRetiree(centre, infirmier, debut, fin, "ADMIN,SECRETAIRE,MEDECIN");
    }
}
