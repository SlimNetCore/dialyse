package com.hemodialyse.backend.characterization.facturation.validation;

import com.hemodialyse.backend.contexts.facturation.publishedlanguage.FacturationValideeEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Outil de test (slice F3) : enregistre les {@link FacturationValideeEvent} publiés par la validation, en plus de leur
 * écouteur de production (Comptabilité). Observe seulement ; ne remplace aucun comportement.
 */
@Component
public class EvenementsFacturationValideeEnregistres {

    private final List<FacturationValideeEvent> recus = new CopyOnWriteArrayList<>();

    @EventListener
    public void enregistrer(FacturationValideeEvent evenement) {
        recus.add(evenement);
    }

    public List<FacturationValideeEvent> recus() {
        return List.copyOf(recus);
    }

    public void oublier() {
        recus.clear();
    }
}
