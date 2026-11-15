package com.hemodialyse.backend.contexts.planification.contrat;

import com.hemodialyse.backend.contexts.planification.application.RoulementImprimableDeLaSemaine;
import com.hemodialyse.backend.contexts.planification.doubles.RoulementImprimableDeLaSemaineEnMemoire;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.LocalDate;
import java.util.UUID;

/** Contrat du roulement imprimé d'une semaine contre le double en mémoire (DOC5e, ADR 0197). */
class RoulementImprimableDeLaSemaineEnMemoireContratTest extends RoulementImprimableDeLaSemaineContrat {

    private final RoulementImprimableDeLaSemaineEnMemoire memoire = new RoulementImprimableDeLaSemaineEnMemoire();

    @Override
    protected RoulementImprimableDeLaSemaine roulement() {
        return memoire;
    }

    @Override
    protected void infirmier(CentreId centre, UUID id, String nom, String prenom, boolean actif) {
        memoire.infirmier(centre, id, nom, prenom, actif);
    }

    @Override
    protected void affectation(CentreId centre, UUID infirmier, UUID salle, UUID creneau, String jours) {
        memoire.affectation(centre, infirmier, salle, creneau, jours);
    }

    @Override
    protected void absence(CentreId centre, UUID infirmier, LocalDate debut, LocalDate fin) {
        memoire.absence(centre, infirmier, debut, fin);
    }

    @Override
    protected void remplacement(CentreId centre, UUID infirmier, LocalDate jour, UUID salle, UUID creneau) {
        memoire.remplacement(centre, infirmier, jour, salle, creneau);
    }
}
