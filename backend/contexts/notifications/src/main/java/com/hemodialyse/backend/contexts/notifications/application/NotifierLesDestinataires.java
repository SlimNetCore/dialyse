package com.hemodialyse.backend.contexts.notifications.application;

import com.hemodialyse.backend.contexts.notifications.domain.Notification;
import com.hemodialyse.backend.contexts.notifications.domain.NotificationId;
import com.hemodialyse.backend.contexts.notifications.domain.NotificationInvalide;
import com.hemodialyse.backend.contexts.notifications.domain.TypeDeNotification;
import com.hemodialyse.backend.contexts.notifications.publishedlanguage.DiffusionEnTempsReel;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Notifier les destinataires : journalise (si l'émetteur le demande) puis diffuse en temps réel. Un fait rejoué à identifiant déterministe
 * déjà journalisé n'est ni dupliqué ni rediffusé. Si le journal échoue, rien n'est diffusé et l'erreur remonte (l'émetteur décide).
 * L'audience est portée dans le contenu sous les clés {@code targetRoles} / {@code targetUserId} que lit le front.
 */
public class NotifierLesDestinataires {

    static final String CLE_DES_ROLES = "targetRoles";
    static final String CLE_DU_DESTINATAIRE = "targetUserId";

    private final NotificationsDuJournal journal;
    private final DiffusionEnTempsReel diffusion;
    private final Horloge horloge;
    private final Identifiants identifiants;

    public NotifierLesDestinataires(NotificationsDuJournal journal, DiffusionEnTempsReel diffusion, Horloge horloge, Identifiants identifiants) {
        this.journal = journal;
        this.diffusion = diffusion;
        this.horloge = horloge;
        this.identifiants = identifiants;
    }

    /** @return vrai si la notification a été émise, faux si l'identifiant déterministe était déjà au journal */
    public boolean executer(Emission emission) {
        if (emission.centre() == null) throw new NotificationInvalide("Une notification est rattachée à un centre");
        UUID valeur = emission.identifiantDeterministe() != null ? emission.identifiantDeterministe() : identifiants.suivant();
        NotificationId id = new NotificationId(valeur);
        Instant maintenant = horloge.maintenant();
        Notification notification = Notification.emise(id, new CentreId(emission.centre()), new TypeDeNotification(emission.type()),
                contenuAvecAudience(emission), emission.audience(), maintenant);
        if (emission.identifiantDeterministe() != null && emission.journalisee() && journal.existe(id)) return false;
        if (emission.journalisee()) journal.enregistrer(notification);
        diffusion.diffuser(emission.centre(), emission.type(), notification.contenu(), emission.journalisee() ? valeur : null, maintenant);
        return true;
    }

    private static Map<String, String> contenuAvecAudience(Emission emission) {
        Map<String, String> contenu = new LinkedHashMap<>(emission.contenu() == null ? Map.of() : emission.contenu());
        contenu.put(CLE_DES_ROLES, emission.audience().rolesEnTexte());
        if (emission.audience().destinataire() != null) contenu.put(CLE_DU_DESTINATAIRE, emission.audience().destinataire());
        return contenu;
    }
}
