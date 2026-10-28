package com.hemodialyse.backend.contexts.planification.contrat;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.planification.application.CentresActifs;
import com.hemodialyse.backend.contexts.planification.application.PersonnelDeLaPresence;
import com.hemodialyse.backend.contexts.planification.doubles.PersonnelDeLaPresenceEnMemoire;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/** Contrat du personnel de la présence contre le double en mémoire. */
class PersonnelDeLaPresenceEnMemoireContratTest extends PersonnelDeLaPresenceContrat {
    private final PersonnelDeLaPresenceEnMemoire monde = new PersonnelDeLaPresenceEnMemoire();

    @Override
    protected PersonnelDeLaPresence personnel() {
        return monde;
    }

    @Override
    protected CentresActifs centresActifs() {
        return monde;
    }

    @Override
    protected void infirmier(CentreId centre, UUID id, String nom, String prenom, boolean actif, boolean habilite) {
        monde.infirmier(centre, id, nom, prenom, actif, habilite);
    }

    @Override
    protected void affectation(CentreId centre, UUID infirmier, Set<DayOfWeek> jours) {
        monde.affectation(centre, infirmier, SALLE, CRENEAU, jours);
    }

    @Override
    protected void absence(CentreId centre, UUID infirmier, LocalDate debut, LocalDate fin) {
        monde.absence(centre, infirmier, debut, fin);
    }

    @Override
    protected void remplacement(CentreId centre, UUID infirmier, LocalDate date) {
        monde.remplacement(centre, infirmier, date, SALLE, CRENEAU);
    }

    @Override
    protected void ratio(CentreId centre, Integer valeur) {
        monde.ratio(centre, valeur);
    }

    @Override
    protected void centre(CentreId centre, boolean actif) {
        monde.centre(centre, actif);
    }
}
