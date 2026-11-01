package com.hemodialyse.backend.contexts.planification.interfaces.http.dto;

import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CreneauId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.SalleId;
import com.hemodialyse.backend.contexts.planification.domain.JourDeLaSemaine;
import com.hemodialyse.backend.contexts.planification.domain.SaisieInvalide;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Affectation saisie : identifiants et jours lus comme du texte, jugés par le domaine (400 si inutilisables).
 */
public record AffectationDemande(UUID salleId, UUID creneauId, List<String> jours) {
    public SalleId salle() {
        return new SalleId(salleId);
    }

    public CreneauId creneau() {
        return new CreneauId(creneauId);
    }

    public Set<JourDeLaSemaine> joursLus() {
        if (jours == null || jours.isEmpty()) {
            throw new SaisieInvalide("Au moins un jour de travail est requis");
        }
        Set<JourDeLaSemaine> lus = EnumSet.noneOf(JourDeLaSemaine.class);
        for (String jour : jours) {
            if (jour == null) {
                throw new SaisieInvalide("Jour de travail illisible");
            }
            try {
                lus.add(JourDeLaSemaine.valueOf(jour));
            } catch (IllegalArgumentException e) {
                throw new SaisieInvalide("Jour de travail inconnu");
            }
        }
        return lus;
    }
}
