package com.hemodialyse.backend.contexts.facturation.infrastructure.messaging;

import com.hemodialyse.backend.contexts.facturation.application.validation.PublicationDeLaValidation;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.FacturationValideeEvent;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Publie l'événement d'intégration par le bus d'événements de l'application, dans la transaction de la validation : les écouteurs
 * transactionnels (Comptabilité) le reçoivent après le commit, et jamais si la validation est annulée.
 */
public class PublicationDeLaValidationSpring implements PublicationDeLaValidation {

    private final ApplicationEventPublisher publisher;

    public PublicationDeLaValidationSpring(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void facturationValidee(FacturationValideeEvent evenement) {
        publisher.publishEvent(evenement);
    }
}
