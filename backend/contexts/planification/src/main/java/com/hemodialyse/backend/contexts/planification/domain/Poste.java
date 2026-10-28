package com.hemodialyse.backend.contexts.planification.domain;

import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CreneauId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.GenerateurId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.SalleId;

import java.util.Objects;

/**
 * Salle, créneau et générateur d'une séance dans une optimisation. Peut être incomplet (placement hérité, patient non placé) :
 * les parties absentes sont nulles. {@code generateurCode} est un libellé pour l'affichage.
 */
public record Poste(SalleId salle, CreneauId creneau, GenerateurId generateur, String generateurCode) {

    public boolean memePlaceQue(Poste autre) {
        return autre != null && Objects.equals(salle, autre.salle) && Objects.equals(creneau, autre.creneau)
                && Objects.equals(generateur, autre.generateur);
    }

    public boolean estComplet() {
        return salle != null && creneau != null && generateur != null;
    }
}
