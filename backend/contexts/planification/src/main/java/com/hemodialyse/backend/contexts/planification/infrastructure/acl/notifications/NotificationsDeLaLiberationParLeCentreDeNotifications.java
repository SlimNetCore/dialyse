package com.hemodialyse.backend.contexts.planification.infrastructure.acl.notifications;

import com.hemodialyse.backend.contexts.notifications.publishedlanguage.DiffusionEnTempsReel;
import com.hemodialyse.backend.contexts.notifications.publishedlanguage.JournalDesNotifications;
import com.hemodialyse.backend.contexts.notifications.publishedlanguage.NotifierEnDeuxTemps;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.NotificationsDeLaLiberation;

import java.time.LocalDate;
import java.util.UUID;

/**
 * ACL sortante de la Planification vers {@code notifications.v1} (ADR 0155) pour son port {@link NotificationsDeLaLiberation} : l'alerte
 * durable {@code LIBERATION_PLACES_EN_ECHEC} (administrateur). Identifiant déterministe (UUID v3 de « type | centre | jour du centre ») : une
 * seule alerte par centre et par jour, même si la nuit est rejouée ou si deux instances travaillent ensemble.
 */
public class NotificationsDeLaLiberationParLeCentreDeNotifications implements NotificationsDeLaLiberation {

    static final String LIBERATION_EN_ECHEC = "LIBERATION_PLACES_EN_ECHEC";

    private final EmissionDesNotificationsDeLaPlanification emission;

    public NotificationsDeLaLiberationParLeCentreDeNotifications(NotifierEnDeuxTemps notifier, DiffusionEnTempsReel diffusion,
                                                                 JournalDesNotifications journal) {
        this.emission = new EmissionDesNotificationsDeLaPlanification(notifier, diffusion, journal);
    }

    @Override
    public boolean liberationEnEchec(UUID centre, LocalDate jour, int nbPatients) {
        UUID id = CatalogueDesNotificationsDeLaPlanification.identifiant(LIBERATION_EN_ECHEC, centre, jour);
        return emission.emettreUneFois(CatalogueDesNotificationsDeLaPlanification.liberationEnEchec(centre, jour, nbPatients, id));
    }
}
