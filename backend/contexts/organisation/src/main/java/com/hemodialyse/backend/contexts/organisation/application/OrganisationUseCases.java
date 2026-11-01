package com.hemodialyse.backend.contexts.organisation.application;

import com.hemodialyse.backend.contexts.organisation.domain.Centre;
import com.hemodialyse.backend.contexts.organisation.domain.Creneau;
import com.hemodialyse.backend.contexts.organisation.domain.Salle;
import com.hemodialyse.backend.contexts.organisation.domain.Societe;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.SocieteId;

import java.util.List;
import java.util.UUID;

public interface OrganisationUseCases {
    SocieteId creerSociete(CommandesOrganisation.CreerSociete commande);

    void modifierSociete(CommandesOrganisation.ModifierSociete commande);

    CentreId creerCentre(CommandesOrganisation.CreerCentre commande);

    void modifierCentre(CommandesOrganisation.ModifierCentre commande);

    void transfererCentre(CommandesOrganisation.TransfererCentre commande);

    List<Centre> listerCentresActifs(SocieteId societeId);

    Centre lireParametresCentre(CentreId centreId);

    List<Societe> listerSocietes();

    Societe consulterSociete(SocieteId societeId);

    List<Centre> listerCentres(SocieteId societeId);

    void ouvrirSalle(CommandesOrganisation.OuvrirSalle commande);

    void fermerSalle(CommandesOrganisation.FermerSalle commande);

    com.hemodialyse.backend.contexts.organisation.domain.CreneauId creerCreneau(CommandesOrganisation.CreerCreneau commande);

    void modifierCreneau(CommandesOrganisation.ModifierCreneau commande);

    List<Salle> listerSallesDuCentre(CentreId centreId);

    List<Creneau> listerCreneaux(CentreId centreId);
}
