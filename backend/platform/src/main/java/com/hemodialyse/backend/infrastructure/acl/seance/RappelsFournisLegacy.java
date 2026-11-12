package com.hemodialyse.backend.infrastructure.acl.seance;

import com.hemodialyse.backend.application.notification.NotificationService;
import com.hemodialyse.backend.contexts.notifications.publishedlanguage.JournalDesNotifications;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.RappelsFournis;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Fournit à la Séance le langage publié {@link RappelsFournis} (ACL vers le centre de notifications legacy) : l'alerte durable
 * {@code SEANCES_A_REGULARISER} pour {@code ADMIN}. Son identifiant est déterministe (UUID v3 de « type | centre | jour du centre ») :
 * une seule alerte par centre et par jour du centre (KB-SEA-35, corrigé ; aucune table ajoutée : le journal
 * {@code notification_evenement} porte l'identifiant).
 * <p>
 * GARANTIE multi-instances : le VERROU CONSULTATIF (centre, jour) pris par le cas d'usage dans la même transaction, pas un repli sur
 * violation de clé. Une seconde instance attend le verrou, puis voit l'identifiant déjà journalisé ({@code existe}) et n'émet rien. Aucun
 * repli sur {@code DataIntegrityViolationException} : sur PostgreSQL un INSERT en échec abandonne la transaction en cours (25P02), un
 * repli ne pourrait pas relire le journal ; si le verrou était contourné, l'erreur remonte, le centre est rendu en incident et il n'y a
 * pas de doublon (la clé primaire du journal l'interdit).
 */
@Component
public class RappelsFournisLegacy implements RappelsFournis {

    static final String TYPE = "SEANCES_A_REGULARISER";

    private final JournalDesNotifications journal;
    private final NotificationService notifications;

    public RappelsFournisLegacy(JournalDesNotifications journal, NotificationService notifications) {
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
        notifications.notifySeancesARegulariser(centre, nbSeances, plusAncienne, id);
        return true;
    }
}
