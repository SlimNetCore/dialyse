package com.hemodialyse.backend.contexts.planification.infrastructure.acl.notifications;

import com.hemodialyse.backend.contexts.notifications.publishedlanguage.DiffusionEnTempsReel;
import com.hemodialyse.backend.contexts.notifications.publishedlanguage.JournalDesNotifications;
import com.hemodialyse.backend.contexts.notifications.publishedlanguage.NotifierEnDeuxTemps;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.NotificationsDeLaPresence;

import java.time.LocalDate;
import java.util.UUID;

/**
 * ACL sortante de la Planification vers le contexte Notifications ({@code notifications.v1}, ADR 0155) pour son port
 * {@link NotificationsDeLaPresence} : les alertes durables {@code INFIRMIER_SOUS_EFFECTIF} (administration, secrétariat, médecins) et
 * {@code INFIRMIER_SUREFFECTIF} (administrateur). Identifiant déterministe (UUID v3 de « type | centre | jour du centre ») : une seule alerte
 * par type, centre et jour du centre, même avec plusieurs instances ou exécutions (KB-PLN-46).
 */
public class NotificationsDeLaPresenceParLeCentreDeNotifications implements NotificationsDeLaPresence {

    static final String SOUS_EFFECTIF = "INFIRMIER_SOUS_EFFECTIF";
    static final String SUREFFECTIF = "INFIRMIER_SUREFFECTIF";

    private final EmissionDesNotificationsDeLaPlanification emission;

    public NotificationsDeLaPresenceParLeCentreDeNotifications(NotifierEnDeuxTemps notifier, DiffusionEnTempsReel diffusion,
                                                               JournalDesNotifications journal) {
        this.emission = new EmissionDesNotificationsDeLaPlanification(notifier, diffusion, journal);
    }

    @Override
    public boolean sousEffectif(UUID centre, LocalDate jour, int nbCases, LocalDate premiereDate) {
        UUID id = CatalogueDesNotificationsDeLaPlanification.identifiant(SOUS_EFFECTIF, centre, jour);
        return emission.emettreUneFois(CatalogueDesNotificationsDeLaPlanification.sousEffectif(centre, nbCases, premiereDate, id));
    }

    @Override
    public boolean sureffectif(UUID centre, LocalDate jour, int nbCases, int nbVacations, int heures, LocalDate premiereDate) {
        UUID id = CatalogueDesNotificationsDeLaPlanification.identifiant(SUREFFECTIF, centre, jour);
        return emission.emettreUneFois(
                CatalogueDesNotificationsDeLaPlanification.sureffectif(centre, nbCases, nbVacations, heures, premiereDate, id));
    }
}
