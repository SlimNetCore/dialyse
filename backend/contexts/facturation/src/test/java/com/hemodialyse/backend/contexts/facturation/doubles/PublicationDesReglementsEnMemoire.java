package com.hemodialyse.backend.contexts.facturation.doubles;

import com.hemodialyse.backend.contexts.facturation.application.reglement.PublicationDesReglements;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.FactureReglementRecordedEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Enregistre les événements publiés par l'enregistrement des règlements.
 */
public class PublicationDesReglementsEnMemoire implements PublicationDesReglements {

    private final List<FactureReglementRecordedEvent> evenements = new ArrayList<>();

    @Override
    public void reglementEnregistre(FactureReglementRecordedEvent evenement) {
        evenements.add(evenement);
    }

    public List<FactureReglementRecordedEvent> publies() {
        return List.copyOf(evenements);
    }
}
