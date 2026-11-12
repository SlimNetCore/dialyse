package com.hemodialyse.backend.contexts.notifications.application;

import com.hemodialyse.backend.contexts.notifications.domain.Notification;
import com.hemodialyse.backend.contexts.notifications.domain.NotificationId;
import com.hemodialyse.backend.contexts.notifications.domain.NotificationInvalide;
import com.hemodialyse.backend.contexts.notifications.domain.TypeDeNotification;
import com.hemodialyse.backend.contexts.notifications.publishedlanguage.NotificationAEnvoyer;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Premier temps de la notification : journaliser tout de suite (si l'émetteur le demande), dans la transaction de l'émetteur ; la diffusion
 * en temps réel est rendue à l'émetteur pour après le commit. Un fait rejoué à identifiant déterministe déjà journalisé n'est ni dupliqué ni
 * rediffusé. Journal en panne : l'erreur remonte si l'identifiant est imposé, sinon elle est avalée et rendue (le temps réel reste prioritaire).
 */
public class JournaliserLaNotification {

    private static final String CLE_DES_ROLES = "targetRoles";
    private static final String CLE_DU_DESTINATAIRE = "targetUserId";

    private final NotificationsDuJournal journal;
    private final Horloge horloge;
    private final Identifiants identifiants;

    public JournaliserLaNotification(NotificationsDuJournal journal, Horloge horloge, Identifiants identifiants) {
        this.journal = journal;
        this.horloge = horloge;
        this.identifiants = identifiants;
    }

    /** @return ce qui reste à diffuser, ou rien si l'identifiant déterministe était déjà au journal */
    public Optional<NotificationAEnvoyer> executer(Emission emission) {
        if (emission.centre() == null) throw new NotificationInvalide("Une notification est rattachée à un centre");
        UUID imposé = emission.identifiantDeterministe();
        if (imposé != null && emission.journalisee() && journal.existe(new NotificationId(imposé))) return Optional.empty();
        UUID valeur = imposé != null ? imposé : identifiants.suivant();
        Instant maintenant = horloge.maintenant();
        Notification notification = Notification.emise(new NotificationId(valeur), new CentreId(emission.centre()),
                new TypeDeNotification(emission.type()), contenuAvecAudience(emission), emission.audience(), maintenant);
        UUID identifiant = null;
        RuntimeException panne = null;
        if (emission.journalisee()) {
            try {
                journal.enregistrer(notification);
                identifiant = valeur;
            } catch (RuntimeException e) {
                if (imposé != null) throw e;
                panne = e;
            }
        }
        return Optional.of(new NotificationAEnvoyer(emission.centre(), emission.type(), notification.contenu(), identifiant, maintenant, panne));
    }

    private static Map<String, String> contenuAvecAudience(Emission emission) {
        Map<String, String> contenu = new LinkedHashMap<>(emission.contenu() == null ? Map.of() : emission.contenu());
        // audience ouverte à tous : le contenu de l'émetteur reste tel quel (parité du JSON diffusé : pas de clé ajoutée)
        if (!emission.audience().estOuverteATous() || contenu.containsKey(CLE_DES_ROLES)) {
            contenu.put(CLE_DES_ROLES, emission.audience().rolesEnTexte());
        }
        if (emission.audience().destinataire() != null) contenu.put(CLE_DU_DESTINATAIRE, emission.audience().destinataire());
        return contenu;
    }
}
