package com.hemodialyse.backend.contexts.facturation.doubles;

import com.hemodialyse.backend.contexts.facturation.application.validation.PublicationDeLaValidation;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.FacturationValideeEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Enregistre les événements publiés par la validation.
 */
public class PublicationEnMemoire implements PublicationDeLaValidation {

    private final List<FacturationValideeEvent> evenements = new ArrayList<>();

    @Override
    public void facturationValidee(FacturationValideeEvent evenement) {
        evenements.add(evenement);
    }

    public List<FacturationValideeEvent> publies() {
        return List.copyOf(evenements);
    }
}
