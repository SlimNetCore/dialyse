package com.hemodialyse.backend.infrastructure.acl.absences;

import com.hemodialyse.backend.application.notification.NotificationService;
import com.hemodialyse.backend.contexts.absencespatients.publishedlanguage.NotificationsDuCentre;
import com.hemodialyse.backend.contexts.notifications.publishedlanguage.JournalDesNotifications;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Fournit au contexte Absences le langage publié {@link NotificationsDuCentre} (ACL vers le centre de notifications legacy,
 * retirée quand les notifications seront un contexte) : l'alerte durable {@code ABSENCES_A_QUALIFIER}. Son identifiant est
 * déterministe (UUID v3 de « type | centre | jour du centre ») : le journal et {@code NotificationService} ignorent une alerte déjà
 * enregistrée, donc une seule alerte par centre et par jour, même avec plusieurs instances du serveur.
 */
@Component
public class NotificationsDuCentreLegacy implements NotificationsDuCentre {

    static final String TYPE = "ABSENCES_A_QUALIFIER";

    private final JournalDesNotifications journal;
    private final NotificationService notifications;

    public NotificationsDuCentreLegacy(JournalDesNotifications journal, NotificationService notifications) {
        this.journal = journal;
        this.notifications = notifications;
    }

    static UUID identifiant(UUID centre, LocalDate jour) {
        return UUID.nameUUIDFromBytes((TYPE + "|" + centre + "|" + jour).getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public boolean signalerLesAbsencesAQualifier(UUID centre, LocalDate jour, long aQualifier, long enRetard) {
        UUID id = identifiant(centre, jour);
        if (journal.existe(id)) {
            return false;
        }
        notifications.notifyAbsencesAQualifier(centre, aQualifier, enRetard, id);
        return true;
    }
}
