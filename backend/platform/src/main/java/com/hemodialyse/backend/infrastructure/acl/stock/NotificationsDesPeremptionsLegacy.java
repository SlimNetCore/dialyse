package com.hemodialyse.backend.infrastructure.acl.stock;

import com.hemodialyse.backend.application.notification.NotificationJournalPort;
import com.hemodialyse.backend.application.notification.NotificationService;
import com.hemodialyse.backend.contexts.stock.publishedlanguage.NotificationsDesPeremptions;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Fournit au contexte Stock le langage publié {@link NotificationsDesPeremptions} (ACL vers le centre de notifications legacy) :
 * l'alerte durable {@code LOTS_PROCHES_PEREMPTION} pour {@code ADMIN} et {@code PHARMACIEN}. Son identifiant est déterministe
 * (UUID v3 de « type | centre | jour du centre ») : une seule alerte par centre et par jour du centre, même avec plusieurs
 * instances du serveur. L'alerte porte les comptes (lots, périmés) et la date la plus proche ; le détail des lots est au tableau de bord du stock.
 */
@Component
public class NotificationsDesPeremptionsLegacy implements NotificationsDesPeremptions {

    static final String TYPE = "LOTS_PROCHES_PEREMPTION";

    private final NotificationJournalPort journal;
    private final NotificationService notifications;

    public NotificationsDesPeremptionsLegacy(NotificationJournalPort journal, NotificationService notifications) {
        this.journal = journal;
        this.notifications = notifications;
    }

    static UUID identifiant(UUID centre, LocalDate jour) {
        return UUID.nameUUIDFromBytes((TYPE + "|" + centre + "|" + jour).getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public boolean signaler(UUID centre, LocalDate jour, List<LotSignale> lots) {
        UUID id = identifiant(centre, jour);
        if (journal.existe(id)) {
            return false;
        }
        long perimes = lots.stream().filter(LotSignale::perime).count();
        try {
            notifications.notifyLotsProchesPeremption(centre, lots.size(), perimes, lots.get(0).datePeremption(), id);
        } catch (DataIntegrityViolationException concurrente) {
            // deux instances ont passé le contrôle « existe » ensemble : l'autre a inséré l'identifiant déterministe d'abord,
            // l'alerte est déjà émise (une seule fois) ; toute autre cause de violation reste une erreur
            if (journal.existe(id)) {
                return false;
            }
            throw concurrente;
        }
        return true;
    }
}
