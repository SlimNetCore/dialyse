package com.hemodialyse.backend.contexts.absencespatients.infrastructure.acl.notifications;

import com.hemodialyse.backend.contexts.absencespatients.publishedlanguage.NotificationsDuCentre;
import com.hemodialyse.backend.contexts.notifications.publishedlanguage.DiffusionEnTempsReel;
import com.hemodialyse.backend.contexts.notifications.publishedlanguage.JournalDesNotifications;
import com.hemodialyse.backend.contexts.notifications.publishedlanguage.NotifierEnDeuxTemps;

import java.time.LocalDate;
import java.util.UUID;

/**
 * ACL sortante des Absences vers {@code notifications.v1} (ADR 0156) : l'alerte durable {@code ABSENCES_A_QUALIFIER}, à identifiant
 * déterministe (UUID v3 de « type | centre | jour du centre ») : déjà au journal, elle n'est ni renvoyée ni dupliquée, même avec plusieurs
 * instances du serveur.
 */
public class NotificationsDuCentreParLeCentreDeNotifications implements NotificationsDuCentre {

    private final EmissionDesNotificationsDesAbsences emission;
    private final JournalDesNotifications journal;

    public NotificationsDuCentreParLeCentreDeNotifications(NotifierEnDeuxTemps notifier, DiffusionEnTempsReel diffusion,
                                                           JournalDesNotifications journal) {
        this.emission = new EmissionDesNotificationsDesAbsences(notifier, diffusion);
        this.journal = journal;
    }

    @Override
    public boolean signalerLesAbsencesAQualifier(UUID centre, LocalDate jour, long aQualifier, long enRetard) {
        UUID id = CatalogueDesNotificationsDesAbsences.identifiantDesAbsencesAQualifier(centre, jour);
        if (journal.existe(id)) {
            return false;
        }
        emission.emettre(CatalogueDesNotificationsDesAbsences.absencesAQualifier(centre, aQualifier, enRetard, id));
        return true;
    }
}
