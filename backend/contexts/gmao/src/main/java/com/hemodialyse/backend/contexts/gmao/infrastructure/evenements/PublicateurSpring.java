package com.hemodialyse.backend.contexts.gmao.infrastructure.evenements;

import com.hemodialyse.backend.contexts.gmao.application.PublicateurDEvenements;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Collection;

/** Adapter du port {@link PublicateurDEvenements} : le registre Spring Modulith enregistre chaque événement dans la transaction. */
public class PublicateurSpring implements PublicateurDEvenements {

    private final ApplicationEventPublisher publisher;

    public PublicateurSpring(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void publier(Collection<?> evenements) {
        evenements.forEach(publisher::publishEvent);
    }
}
