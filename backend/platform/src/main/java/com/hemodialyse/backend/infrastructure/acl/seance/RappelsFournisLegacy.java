package com.hemodialyse.backend.infrastructure.acl.seance;

import com.hemodialyse.backend.application.notification.NotificationJournalPort;
import com.hemodialyse.backend.application.notification.NotificationService;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.RappelsFournis;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Fournit à la Séance le langage publié {@link RappelsFournis} (ACL vers le centre de notifications legacy) : l'alerte durable
 * {@code SEANCES_A_REGULARISER} pour {@code ADMIN}. Son identifiant est déterministe (UUID v3 de « type | centre | jour du centre ») :
 * une seule alerte par centre et par jour du centre, même avec plusieurs instances du serveur (KB-SEA-35, corrigé ; aucune table
 * ajoutée : le journal {@code notification_evenement} porte l'identifiant).
 */
@Component
public class RappelsFournisLegacy implements RappelsFournis {

    static final String TYPE = "SEANCES_A_REGULARISER";

    private final NotificationJournalPort journal;
    private final NotificationService notifications;

    public RappelsFournisLegacy(NotificationJournalPort journal, NotificationService notifications) {
        this.journal = journal;
        this.notifications = notifications;
    }

    static UUID identifiant(UUID centre, LocalDate jour) {
        return UUID.nameUUIDFromBytes((TYPE + "|" + centre + "|" + jour).getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public boolean rappeler(UUID centre, LocalDate jour, long nbSeances, LocalDate plusAncienne) {
        UUID id = identifiant(centre, jour);
        if (journal.existe(id)) {
            return false;
        }
        try {
            notifications.notifySeancesARegulariser(centre, nbSeances, plusAncienne, id);
        } catch (DataIntegrityViolationException concurrente) {
            // deux instances ont passé le contrôle « existe » ensemble : l'autre a inséré l'identifiant déterministe d'abord, le
            // rappel est déjà émis (une seule fois) ; toute autre cause de violation reste une erreur
            if (journal.existe(id)) {
                return false;
            }
            throw concurrente;
        }
        return true;
    }
}
