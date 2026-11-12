package com.hemodialyse.backend.contexts.notifications.interfaces.publie;

import com.hemodialyse.backend.contexts.notifications.application.Emission;
import com.hemodialyse.backend.contexts.notifications.application.NotifierLesDestinataires;
import com.hemodialyse.backend.contexts.notifications.domain.Audience;
import com.hemodialyse.backend.contexts.notifications.publishedlanguage.DemandeDeNotification;
import com.hemodialyse.backend.contexts.notifications.publishedlanguage.Notifier;

/** {@code notifications.v1} : traduit la demande d'un émetteur vers le cas d'usage {@link NotifierLesDestinataires}. */
public class NotifierPublie implements Notifier {

    private final NotifierLesDestinataires cas;

    public NotifierPublie(NotifierLesDestinataires cas) {
        this.cas = cas;
    }

    @Override
    public boolean notifier(DemandeDeNotification demande) {
        var audience = demande.audience() == null ? com.hemodialyse.backend.contexts.notifications.publishedlanguage.Audience.tous() : demande.audience();
        return cas.executer(new Emission(demande.centre(), demande.type(), demande.contenu(),
                Audience.desRolesEtUnDestinataire(audience.roles(), audience.destinataire()), demande.identifiantDeterministe(),
                demande.journalisee()));
    }
}
