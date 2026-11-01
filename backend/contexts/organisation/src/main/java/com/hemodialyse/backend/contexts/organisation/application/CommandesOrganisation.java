package com.hemodialyse.backend.contexts.organisation.application;

import com.hemodialyse.backend.contexts.organisation.domain.CreneauId;
import com.hemodialyse.backend.contexts.organisation.domain.SalleId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.SocieteId;

public final class CommandesOrganisation {
    private CommandesOrganisation() {
    }

    public record CreerSociete(String code, String raisonSociale) {
    }

    public record ModifierSociete(SocieteId id, String code, String raisonSociale, boolean actif) {
    }

    public record CreerCentre(SocieteId societeId, String code, String nom, String fuseauHoraire,
                              int capaciteTheorique) {
    }

    public record ModifierCentre(CentreId id, String nom, String fuseauHoraire, int capaciteTheorique, boolean actif) {
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
