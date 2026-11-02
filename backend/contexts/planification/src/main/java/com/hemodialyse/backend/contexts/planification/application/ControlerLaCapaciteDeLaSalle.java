package com.hemodialyse.backend.contexts.planification.application;

import com.hemodialyse.backend.contexts.planification.domain.CapaciteDeLaSalle;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.CapaciteDesSalles;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.UUID;

/**
 * Refuse l'affectation d'un générateur à une salle qui n'a plus de place (RG-PLN-061). Sans salle, rien à contrôler ; une salle d'un
 * autre centre est introuvable (aucune limite). Le générateur déjà dans la salle n'est pas compté. Lecture seule.
 */
public class ControlerLaCapaciteDeLaSalle implements CapaciteDesSalles {

    private final SalleDuCentre salles;

    public ControlerLaCapaciteDeLaSalle(SalleDuCentre salles) {
        this.salles = salles;
    }

    @Override
    public void verifierAffectation(UUID centre, UUID salle, UUID equipement) {
        if (salle == null) return;
        salles.parIdentifiant(new CentreId(centre), salle).ifPresent(vue -> {
            int deja = (int) vue.generateurs().stream().filter(g -> !g.id().equals(equipement)).count();
            if (CapaciteDeLaSalle.depasseraitAvecUnGenerateurDePlus(vue.capacite(), deja)) {
                throw new CapaciteDeLaSalleDepassee(vue.nom(), vue.capacite());
            }
        });
    }
}
