package com.hemodialyse.backend.domain.planning.model;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * Paramétrage du planning d'un centre : jours de la semaine où l'on dialyse et salles réservées aux patients à
 * risque infectieux (isolement). Par défaut, le centre dialyse tous les jours et n'a aucune salle d'isolement.
 */
public record PlanningParametres(Set<JourSemaine> joursOuverts, Set<UUID> sallesIsolement) {

    public PlanningParametres {
        if (joursOuverts == null || joursOuverts.isEmpty()) {
            throw new IllegalArgumentException("Au moins un jour d'ouverture est requis");
        }
        joursOuverts = Set.copyOf(EnumSet.copyOf(joursOuverts));
        sallesIsolement = sallesIsolement == null ? Set.of() : Set.copyOf(sallesIsolement);
    }

    public static PlanningParametres parDefaut() {
        return new PlanningParametres(EnumSet.allOf(JourSemaine.class), Set.of());
    }
}
