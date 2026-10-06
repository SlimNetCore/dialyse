package com.hemodialyse.backend.infrastructure.optimisation.timefold.patients;

import com.hemodialyse.backend.domain.planning.model.JourSemaine;

import java.util.List;

/**
 * Valeur de planification : les jours de dialyse d'un patient. Un patient aux jours prescrits n'a qu'un schéma possible ;
 * un patient dont l'optimisation choisit les jours en a plusieurs, notés par leur écart d'espacement au meilleur.
 *
 * @param penaliteEspacement écart (points) entre l'espacement de ce schéma et celui du meilleur schéma candidat
 */
public record SchemaJours(List<JourSemaine> jours, int masque, int penaliteEspacement) {

    public SchemaJours(List<JourSemaine> jours, int penaliteEspacement) {
        this(List.copyOf(jours.stream().sorted().toList()), PlacementPatient.masque(jours), penaliteEspacement);
    }

    public SchemaJours {
        jours = List.copyOf(jours);
    }

    public static SchemaJours fixe(List<JourSemaine> jours) {
        return new SchemaJours(jours, 0);
    }
}
