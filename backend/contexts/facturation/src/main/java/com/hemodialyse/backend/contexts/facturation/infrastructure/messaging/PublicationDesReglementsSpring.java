package com.hemodialyse.backend.contexts.facturation.infrastructure.messaging;

import com.hemodialyse.backend.contexts.facturation.application.reglement.PublicationDesReglements;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.FactureReglementRecordedEvent;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Publie l'événement d'intégration par le bus d'événements de l'application, dans la transaction de l'enregistrement : les
 * écouteurs transactionnels (Comptabilité) le reçoivent après le commit, et jamais si l'enregistrement est annulé.
 */
public class PublicationDesReglementsSpring implements PublicationDesReglements {

    private final ApplicationEventPublisher publisher;

    public PublicationDesReglementsSpring(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void reglementEnregistre(FactureReglementRecordedEvent evenement) {
        publisher.publishEvent(evenement);
    }
}
