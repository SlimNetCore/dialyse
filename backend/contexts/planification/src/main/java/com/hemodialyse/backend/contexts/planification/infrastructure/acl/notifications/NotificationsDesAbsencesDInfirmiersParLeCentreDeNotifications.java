package com.hemodialyse.backend.contexts.planification.infrastructure.acl.notifications;

import com.hemodialyse.backend.contexts.notifications.publishedlanguage.DiffusionEnTempsReel;
import com.hemodialyse.backend.contexts.notifications.publishedlanguage.JournalDesNotifications;
import com.hemodialyse.backend.contexts.notifications.publishedlanguage.NotifierEnDeuxTemps;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.NotificationsDesAbsencesDInfirmiers;

import java.time.LocalDate;
import java.util.UUID;

/**
 * ACL sortante de la Planification vers {@code notifications.v1} (ADR 0155) pour son port {@link NotificationsDesAbsencesDInfirmiers} :
 * journal durable puis diffusion temps réel du centre, qui rafraîchit les écrans de planification.
 */
public class NotificationsDesAbsencesDInfirmiersParLeCentreDeNotifications implements NotificationsDesAbsencesDInfirmiers {

    private final EmissionDesNotificationsDeLaPlanification emission;

    public NotificationsDesAbsencesDInfirmiersParLeCentreDeNotifications(NotifierEnDeuxTemps notifier, DiffusionEnTempsReel diffusion,
                                                                         JournalDesNotifications journal) {
        this.emission = new EmissionDesNotificationsDeLaPlanification(notifier, diffusion, journal);
    }

    @Override
    public void absenceEnregistree(UUID centre, String infirmier, LocalDate debut, LocalDate fin, boolean prevenirAdministrateur) {
        emission.emettre(CatalogueDesNotificationsDeLaPlanification.absenceEnregistree(centre, infirmier, debut, fin, prevenirAdministrateur), false);
    }

    @Override
    public void absenceDeclareeParLInfirmier(UUID centre, String infirmier, LocalDate debut, LocalDate fin) {
        emission.emettre(CatalogueDesNotificationsDeLaPlanification.absenceDeclaree(centre, infirmier, debut, fin), false);
    }

    @Override
    public void absenceRetireeParLAdministration(UUID centre, String infirmier, LocalDate debut, LocalDate fin,
                                                 boolean prevenirAdministrateur) {
        emission.emettre(CatalogueDesNotificationsDeLaPlanification.absenceRetiree(centre, infirmier, debut, fin,
                prevenirAdministrateur ? "ADMIN,MEDECIN" : "MEDECIN"), false);
    }

    @Override
    public void absenceRetireeParLInfirmier(UUID centre, String infirmier, LocalDate debut, LocalDate fin) {
        emission.emettre(CatalogueDesNotificationsDeLaPlanification.absenceRetiree(centre, infirmier, debut, fin, "ADMIN,SECRETAIRE,MEDECIN"), false);
    }
}
