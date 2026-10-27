package com.hemodialyse.backend.infrastructure.acl.planification;

import com.hemodialyse.backend.application.notification.NotificationJournalPort;
import com.hemodialyse.backend.application.notification.NotificationService;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.NotificationsDeLaPresence;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Fournit au contexte Planification son port consommé {@link NotificationsDeLaPresence} (ACL vers le centre de notifications legacy) :
 * les alertes durables {@code INFIRMIER_SOUS_EFFECTIF} (administration, secrétariat, médecins) et {@code INFIRMIER_SUREFFECTIF}
 * (administrateur). Chaque identifiant est déterministe (UUID v3 de « type | centre | jour du centre ») : une seule alerte par type,
 * centre et jour du centre, même si plusieurs instances du serveur ou plusieurs exécutions contrôlent ensemble (KB-PLN-46).
 */
@Component
public class NotificationsDeLaPresenceLegacy implements NotificationsDeLaPresence {

    static final String SOUS_EFFECTIF = "INFIRMIER_SOUS_EFFECTIF";
    static final String SUREFFECTIF = "INFIRMIER_SUREFFECTIF";

    private final NotificationJournalPort journal;
    private final NotificationService notifications;

    public NotificationsDeLaPresenceLegacy(NotificationJournalPort journal, NotificationService notifications) {
        this.journal = journal;
        this.notifications = notifications;
    }

    static UUID identifiant(String type, UUID centre, LocalDate jour) {
        return UUID.nameUUIDFromBytes((type + "|" + centre + "|" + jour).getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public boolean sousEffectif(UUID centre, LocalDate jour, int nbCases, LocalDate premiereDate) {
        UUID id = identifiant(SOUS_EFFECTIF, centre, jour);
        return emettre(id, () -> notifications.notifyPresenceSousEffectif(centre, nbCases, premiereDate, id));
    }

    @Override
    public boolean sureffectif(UUID centre, LocalDate jour, int nbCases, int nbVacations, int heures, LocalDate premiereDate) {
        UUID id = identifiant(SUREFFECTIF, centre, jour);
        return emettre(id, () -> notifications.notifyPresenceSureffectif(centre, nbCases, nbVacations, heures, premiereDate, id));
    }

    private boolean emettre(UUID id, Runnable envoi) {
        if (journal.existe(id)) {
            return false;
        }
        try {
            envoi.run();
        } catch (DataIntegrityViolationException concurrente) {
            // une autre instance a inséré l'identifiant déterministe d'abord : l'alerte est déjà émise (une seule fois)
            if (journal.existe(id)) {
                return false;
            }
            throw concurrente;
        }
        return true;
    }
}
