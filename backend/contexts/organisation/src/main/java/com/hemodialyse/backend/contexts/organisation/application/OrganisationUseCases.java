package com.hemodialyse.backend.contexts.organisation.application;

import com.hemodialyse.backend.contexts.organisation.domain.Centre;
import com.hemodialyse.backend.contexts.organisation.domain.Creneau;
import com.hemodialyse.backend.contexts.organisation.domain.LogoDeSociete;
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

    com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult<Societe> listerSocietesParPage(
            String recherche, int page, int size);

    Societe consulterSociete(SocieteId societeId);

    List<Centre> listerCentres(SocieteId societeId);

    LogoDeSociete lireLogoSociete(SocieteId societeId);

    void televerserLogoSociete(SocieteId societeId, byte[] contenu);

    void supprimerLogoSociete(SocieteId societeId);

    java.util.Set<SocieteId> societesAvecLogo(java.util.Collection<SocieteId> ids);

    void ouvrirSalle(CommandesOrganisation.OuvrirSalle commande);

    void fermerSalle(CommandesOrganisation.FermerSalle commande);

    com.hemodialyse.backend.contexts.organisation.domain.CreneauId creerCreneau(CommandesOrganisation.CreerCreneau commande);

    void modifierCreneau(CommandesOrganisation.ModifierCreneau commande);

    List<Salle> listerSallesDuCentre(CentreId centreId);

    List<Creneau> listerCreneaux(CentreId centreId);
}
