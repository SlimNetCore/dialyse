package com.hemodialyse.backend.infrastructure.acl.planification;

import com.hemodialyse.backend.application.notification.NotificationService;
import com.hemodialyse.backend.contexts.notifications.publishedlanguage.JournalDesNotifications;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.NotificationsDeLaLiberation;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Fournit au contexte Planification son port consommé {@link NotificationsDeLaLiberation} (ACL vers le centre de notifications
 * legacy) : l'alerte durable {@code LIBERATION_PLACES_EN_ECHEC} (administrateur). Identifiant déterministe (UUID v3 de « type | centre |
 * jour du centre ») : une seule alerte par centre et par jour, même si la nuit est rejouée ou si deux instances travaillent ensemble.
 */
@Component
public class NotificationsDeLaLiberationLegacy implements NotificationsDeLaLiberation {

    static final String LIBERATION_EN_ECHEC = "LIBERATION_PLACES_EN_ECHEC";

    private final JournalDesNotifications journal;
    private final NotificationService notifications;

    public NotificationsDeLaLiberationLegacy(JournalDesNotifications journal, NotificationService notifications) {
        this.journal = journal;
        this.notifications = notifications;
    }

    static UUID identifiant(UUID centre, LocalDate jour) {
        return UUID.nameUUIDFromBytes((LIBERATION_EN_ECHEC + "|" + centre + "|" + jour).getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public boolean liberationEnEchec(UUID centre, LocalDate jour, int nbPatients) {
        UUID id = identifiant(centre, jour);
        if (journal.existe(id)) {
            return false;
        }
        try {
            notifications.notifyLiberationDesPlacesEnEchec(centre, jour, nbPatients, id);
        } catch (DataIntegrityViolationException concurrente) {
            // une autre instance a inséré l'identifiant déterministe d'abord : l'alerte est déjà émise
            if (journal.existe(id)) {
                return false;
            }
            throw concurrente;
        }
        return true;
    }
}
