package com.hemodialyse.backend.contexts.organisation.application;

import com.hemodialyse.backend.contexts.organisation.domain.Coordonnees;
import com.hemodialyse.backend.contexts.organisation.domain.CreneauId;
import com.hemodialyse.backend.contexts.organisation.domain.SalleId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.SocieteId;

public final class CommandesOrganisation {
    private CommandesOrganisation() {
    }

    public record CreerSociete(String code, String raisonSociale, String nif, String nis, String rc,
                               Coordonnees coordonnees, String piedDePage) {
    }

    public record ModifierSociete(SocieteId id, String code, String raisonSociale, String nif, String nis,
                                  String rc, Coordonnees coordonnees, String piedDePage, boolean actif) {
    }

    public record CreerCentre(SocieteId societeId, String code, String nom, Coordonnees coordonnees,
                              String fuseauHoraire) {
    }

    public record ModifierCentre(CentreId id, String code, String nom, Coordonnees coordonnees,
                                 String fuseauHoraire, boolean actif) {
    }

    public record TransfererCentre(CentreId id, SocieteId nouvelleSociete) {
    }

    public record OuvrirSalle(CentreId centreId, SalleId salleId) {
    }

    public record FermerSalle(CentreId centreId, SalleId salleId) {
    }

    public record CreerCreneau(CentreId centreId, String code, String libelle) {
    }

    public record ModifierCreneau(CentreId centreId, CreneauId id, String code, String libelle) {
    }
}
