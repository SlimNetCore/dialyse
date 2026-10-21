package com.hemodialyse.backend.characterization.facturation.reglements;

import com.hemodialyse.backend.contexts.facturation.publishedlanguage.FactureReglementRecordedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Observateur de test : enregistre les {@code FactureReglementRecordedEvent} réellement publiés par
 * {@code EnregistrerUnReglement} (slice F4, contexte facturation), en plus de l'écouteur de production (Comptabilité). Observe seulement.
 */
@Component
public class ReglementsEnregistresObserves {

    private final List<FactureReglementRecordedEvent> recus = new CopyOnWriteArrayList<>();

    @EventListener
    public void enregistrer(FactureReglementRecordedEvent evenement) {
        recus.add(evenement);
    }

    public List<FactureReglementRecordedEvent> recus() {
        return List.copyOf(recus);
    }

    public void oublier() {
        recus.clear();
    }
}
