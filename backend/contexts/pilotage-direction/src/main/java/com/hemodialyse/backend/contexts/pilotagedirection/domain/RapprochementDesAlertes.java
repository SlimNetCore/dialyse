package com.hemodialyse.backend.contexts.pilotagedirection.domain;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.ArrayList;

/**
 * Rapproche les alertes actuellement levées de celles déjà ouvertes dans l'historique : OUVRE celles qui n'ont pas de ligne ouverte de même
 * clé (centre, code), RÉSOUT celles dont la clé n'est plus levée. Une alerte déjà ouverte n'est jamais touchée : sa valeur, sa gravité et le
 * nom du centre restent FIGÉS à la première apparition (KB-DIR-04, R2 option (a)). Aucune règle par jour. Deux alertes levées de même clé
 * absentes de l'historique ouvrent deux lignes (reproduit tel quel : la politique ne lève jamais deux fois le même code pour un centre).
 */
public final class RapprochementDesAlertes {

    private RapprochementDesAlertes() {
    }

    public static Rapprochement rapprocher(List<AlerteOuverte> ouvertes, List<AlerteLevee> levees) {
        Set<CleDAlerte> clesOuvertes = new HashSet<>();
        for (AlerteOuverte o : ouvertes) clesOuvertes.add(o.cle());
        Set<CleDAlerte> clesLevees = new HashSet<>();
        List<AlerteLevee> aOuvrir = new ArrayList<>();
        for (AlerteLevee a : levees) {
            clesLevees.add(a.cle());
            if (!clesOuvertes.contains(a.cle())) aOuvrir.add(a);
        }
        List<UUID> aResoudre = new ArrayList<>();
        for (AlerteOuverte o : ouvertes) {
            if (!clesLevees.contains(o.cle())) aResoudre.add(o.id());
        }
        return new Rapprochement(List.copyOf(aOuvrir), List.copyOf(aResoudre));
    }

    public record Rapprochement(List<AlerteLevee> aOuvrir, List<UUID> aResoudre) {
    }
}
