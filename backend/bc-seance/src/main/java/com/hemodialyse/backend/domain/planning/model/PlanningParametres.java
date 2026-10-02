package com.hemodialyse.backend.domain.planning.model;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * Paramétrage du planning d'un centre : jours de la semaine où l'on dialyse, salles réservées aux patients à
 * risque infectieux (isolement) et ratio de sécurité (nombre maximal de patients par infirmier). Par défaut, le centre
 * dialyse tous les jours, n'a aucune salle d'isolement et applique un infirmier pour {@value #RATIO_PAR_DEFAUT} patients.
 */
public record PlanningParametres(Set<JourSemaine> joursOuverts, Set<UUID> sallesIsolement, int patientsParInfirmier) {

    public static final int RATIO_PAR_DEFAUT = 4;
    public static final int RATIO_MAX = 20;

    public PlanningParametres {
        if (joursOuverts == null || joursOuverts.isEmpty()) {
            throw new IllegalArgumentException("Au moins un jour d'ouverture est requis");
        }
        if (patientsParInfirmier < 1 || patientsParInfirmier > RATIO_MAX) {
            throw new IllegalArgumentException("Le ratio patients par infirmier doit être compris entre 1 et " + RATIO_MAX);
        }
        joursOuverts = Set.copyOf(EnumSet.copyOf(joursOuverts));
        sallesIsolement = sallesIsolement == null ? Set.of() : Set.copyOf(sallesIsolement);
    }

    public PlanningParametres(Set<JourSemaine> joursOuverts, Set<UUID> sallesIsolement) {
        this(joursOuverts, sallesIsolement, RATIO_PAR_DEFAUT);
    }

    public static PlanningParametres parDefaut() {
        return new PlanningParametres(EnumSet.allOf(JourSemaine.class), Set.of(), RATIO_PAR_DEFAUT);
    }
}
