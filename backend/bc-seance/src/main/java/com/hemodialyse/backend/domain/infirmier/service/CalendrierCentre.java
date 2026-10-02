package com.hemodialyse.backend.domain.infirmier.service;

import com.hemodialyse.backend.domain.planning.model.JourSemaine;
import com.hemodialyse.backend.domain.planning.model.Planning.DonneesPlanning;
import com.hemodialyse.backend.domain.planning.model.Planning.Fermeture;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

/**
 * Jours où le centre dialyse : jours d'ouverture hebdomadaires, hors fériés et fermetures exceptionnelles.
 */
final class CalendrierCentre {

    private final Set<JourSemaine> ouverts;
    private final Set<LocalDate> fermetures = new HashSet<>();

    private CalendrierCentre(Set<JourSemaine> ouverts) {
        this.ouverts = ouverts;
    }

    static CalendrierCentre de(DonneesPlanning planning) {
        Set<JourSemaine> ouverts = planning.joursOuverts() == null || planning.joursOuverts().isEmpty()
                ? EnumSet.allOf(JourSemaine.class) : EnumSet.copyOf(planning.joursOuverts());
        CalendrierCentre calendrier = new CalendrierCentre(ouverts);
        if (planning.fermetures() != null) {
            for (Fermeture f : planning.fermetures()) calendrier.fermetures.add(f.date());
        }
        return calendrier;
    }

    boolean ferme(LocalDate date) {
        return !ouverts.contains(JourSemaine.de(date.getDayOfWeek())) || fermetures.contains(date);
    }
}
