package com.hemodialyse.backend.contexts.stock.infrastructure.acl.notifications;

import com.hemodialyse.backend.contexts.notifications.publishedlanguage.DiffusionEnTempsReel;
import com.hemodialyse.backend.contexts.notifications.publishedlanguage.JournalDesNotifications;
import com.hemodialyse.backend.contexts.notifications.publishedlanguage.NotifierEnDeuxTemps;
import com.hemodialyse.backend.contexts.stock.publishedlanguage.NotificationsDesPeremptions;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * ACL sortante du Stock vers {@code notifications.v1} (ADR 0156) : l'alerte durable {@code LOTS_PROCHES_PEREMPTION} pour {@code ADMIN} et
 * {@code PHARMACIEN}, à identifiant déterministe (une seule par centre et par jour du centre, même avec plusieurs instances). Elle porte
 * les comptes (lots, périmés) et la date la plus proche ; le détail des lots est au tableau de bord du stock.
 */
public class NotificationsDesPeremptionsParLeCentreDeNotifications implements NotificationsDesPeremptions {

    private final EmissionDesNotificationsDuStock emission;
    private final JournalDesNotifications journal;

    public NotificationsDesPeremptionsParLeCentreDeNotifications(NotifierEnDeuxTemps notifier, DiffusionEnTempsReel diffusion,
                                                                 JournalDesNotifications journal) {
        this.emission = new EmissionDesNotificationsDuStock(notifier, diffusion);
        this.journal = journal;
    }

    @Override
    public boolean signaler(UUID centre, LocalDate jour, List<LotSignale> lots) {
        UUID id = CatalogueDesNotificationsDuStock.identifiantDesLotsProchesPeremption(centre, jour);
        if (journal.existe(id)) {
            return false;
        }
        long perimes = lots.stream().filter(LotSignale::perime).count();
        try {
            emission.emettre(CatalogueDesNotificationsDuStock.lotsProchesPeremption(centre, lots.size(), perimes,
                    lots.get(0).datePeremption(), id));
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
