package com.hemodialyse.backend.contexts.planification.domain;

import com.hemodialyse.backend.contexts.planification.domain.Presence.Fermeture;
import com.hemodialyse.backend.contexts.planification.domain.Presence.PlanningDesPlaces;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Les jours où le centre dialyse : jours d'ouverture hebdomadaires (aucun renseigné = tous), hors fériés et fermetures
 * exceptionnelles.
 */
public final class OuvertureDuCentre {

    private final Set<JourDeLaSemaine> ouverts;
    private final Map<LocalDate, String> fermetures = new HashMap<>();

    private OuvertureDuCentre(Set<JourDeLaSemaine> ouverts) {
        this.ouverts = ouverts;
    }

    public static OuvertureDuCentre de(PlanningDesPlaces planning) {
        Set<JourDeLaSemaine> ouverts = planning.joursOuverts() == null || planning.joursOuverts().isEmpty()
                ? EnumSet.allOf(JourDeLaSemaine.class) : EnumSet.copyOf(planning.joursOuverts());
        OuvertureDuCentre ouverture = new OuvertureDuCentre(ouverts);
        if (planning.fermetures() != null) {
            for (Fermeture f : planning.fermetures()) ouverture.fermetures.putIfAbsent(f.date(), f.motif() == null ? "" : f.motif());
        }
        return ouverture;
    }

    public boolean ouvertHebdomadaire(JourDeLaSemaine jour) {
        return ouverts.contains(jour);
    }

    /** Motif de la fermeture datée de ce jour, ou {@code null}. */
    public String motifDeFermeture(LocalDate date) {
        return fermetures.get(date);
    }

    public boolean ferme(LocalDate date) {
        return !ouverts.contains(JourDeLaSemaine.de(date.getDayOfWeek())) || fermetures.containsKey(date);
    }
}
