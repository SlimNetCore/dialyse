package com.hemodialyse.backend.contexts.notifications.interfaces.publie;

import com.hemodialyse.backend.contexts.notifications.application.Emission;
import com.hemodialyse.backend.contexts.notifications.application.JournaliserLaNotification;
import com.hemodialyse.backend.contexts.notifications.domain.Audience;
import com.hemodialyse.backend.contexts.notifications.publishedlanguage.DemandeDeNotification;
import com.hemodialyse.backend.contexts.notifications.publishedlanguage.NotificationAEnvoyer;
import com.hemodialyse.backend.contexts.notifications.publishedlanguage.NotifierEnDeuxTemps;

import java.util.Optional;

/** {@code notifications.v1} : traduit la demande d'un émetteur vers le premier temps de la notification. */
public class NotifierEnDeuxTempsPublie implements NotifierEnDeuxTemps {

    private final JournaliserLaNotification cas;

    public NotifierEnDeuxTempsPublie(JournaliserLaNotification cas) {
        this.cas = cas;
    }

    @Override
    public Optional<NotificationAEnvoyer> journaliser(DemandeDeNotification demande) {
        var audience = demande.audience() == null ? com.hemodialyse.backend.contexts.notifications.publishedlanguage.Audience.tous() : demande.audience();
        return cas.executer(new Emission(demande.centre(), demande.type(), demande.contenu(),
                Audience.desRolesEtUnDestinataire(audience.roles(), audience.destinataire()), demande.identifiantDeterministe(),
                demande.journalisee()));
    }
}
