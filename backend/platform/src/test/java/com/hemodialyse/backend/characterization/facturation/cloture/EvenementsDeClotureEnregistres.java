package com.hemodialyse.backend.characterization.facturation.cloture;

import com.hemodialyse.backend.contexts.facturation.publishedlanguage.PeriodeDeFacturationCloturee;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.PeriodeDeFacturationRouverte;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Outil de test (slice F7) : enregistre les événements de la clôture mensuelle publiés par la Facturation. Observe seulement ; la
 * clôture n'a aucun écouteur de production.
 */
@Component
public class EvenementsDeClotureEnregistres {

    private final List<Object> recus = new CopyOnWriteArrayList<>();

    @EventListener
    public void cloturee(PeriodeDeFacturationCloturee evenement) {
        recus.add(evenement);
    }

    @EventListener
    public void rouverte(PeriodeDeFacturationRouverte evenement) {
        recus.add(evenement);
    }

    public List<Object> recus() {
        return List.copyOf(recus);
    }

    public void oublier() {
        recus.clear();
    }
}
