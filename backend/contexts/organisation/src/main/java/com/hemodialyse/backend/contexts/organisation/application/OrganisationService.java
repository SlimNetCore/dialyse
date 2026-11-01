package com.hemodialyse.backend.contexts.organisation.application;

import com.hemodialyse.backend.contexts.organisation.domain.Centre;
import com.hemodialyse.backend.contexts.organisation.domain.CentreRepository;
import com.hemodialyse.backend.contexts.organisation.domain.Creneau;
import com.hemodialyse.backend.contexts.organisation.domain.CreneauId;
import com.hemodialyse.backend.contexts.organisation.domain.CreneauRepository;
import com.hemodialyse.backend.contexts.organisation.domain.Salle;
import com.hemodialyse.backend.contexts.organisation.domain.SalleId;
import com.hemodialyse.backend.contexts.organisation.domain.SalleRepository;
import com.hemodialyse.backend.contexts.organisation.domain.Societe;
import com.hemodialyse.backend.contexts.organisation.domain.SocieteRepository;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.SocieteId;

import java.util.List;
import java.util.UUID;

public class OrganisationService implements OrganisationUseCases {

    private final SocieteRepository societes;
    private final CentreRepository centres;
    private final SalleRepository salles;
    private final CreneauRepository creneaux;

    public OrganisationService(SocieteRepository societes, CentreRepository centres, SalleRepository salles, CreneauRepository creneaux) {
        this.societes = societes;
        this.centres = centres;
        this.salles = salles;
        this.creneaux = creneaux;
    }

    @Override
    public SocieteId creerSociete(CommandesOrganisation.CreerSociete commande) {
        SocieteId id = new SocieteId(UUID.randomUUID());
        Societe s = new Societe(id, commande.code(), commande.raisonSociale(), true);
        societes.sauvegarder(s);
        return id;
    }

    @Override
    public void modifierSociete(CommandesOrganisation.ModifierSociete commande) {
        Societe s = societes.trouver(commande.id()).orElseThrow(() -> new IllegalArgumentException("Societe inconnue"));
        s.modifier(commande.code(), commande.raisonSociale(), commande.actif());
        societes.sauvegarder(s);
    }

    @Override
    public CentreId creerCentre(CommandesOrganisation.CreerCentre commande) {
        if (societes.trouver(commande.societeId()).isEmpty()) throw new IllegalArgumentException("Societe inconnue");
        CentreId id = new CentreId(UUID.randomUUID());
        Centre c = new Centre(id, commande.societeId(), commande.code(), commande.nom(), commande.fuseauHoraire(), commande.capaciteTheorique(), true);
        centres.sauvegarder(c);
        return id;
    }

    @Override
    public void modifierCentre(CommandesOrganisation.ModifierCentre commande) {
        Centre c = centres.trouver(commande.id()).orElseThrow(() -> new IllegalArgumentException("Centre inconnu"));
        c.modifierParametres(commande.nom(), commande.fuseauHoraire(), commande.capaciteTheorique(), commande.actif());
        centres.sauvegarder(c);
    }

    @Override
    public void transfererCentre(CommandesOrganisation.TransfererCentre commande) {
        Centre c = centres.trouver(commande.id()).orElseThrow(() -> new IllegalArgumentException("Centre inconnu"));
        if (societes.trouver(commande.nouvelleSociete()).isEmpty())
            throw new IllegalArgumentException("Societe inconnue");
        c.transferer(commande.nouvelleSociete());
        centres.sauvegarder(c);
    }

    @Override
    public List<Centre> listerCentresActifs(SocieteId societeId) {
        return centres.listerActifs(societeId);
    }

    @Override
    public Centre lireParametresCentre(CentreId centreId) {
        return centres.trouver(centreId).orElseThrow(() -> new IllegalArgumentException("Centre inconnu"));
    }

    @Override
    public List<Societe> listerSocietes() {
        return societes.lister();
    }

    @Override
    public void ouvrirSalle(CommandesOrganisation.OuvrirSalle commande) {
        Salle s = salles.trouver(commande.centreId(), commande.salleId()).orElseThrow(() -> new IllegalArgumentException("Salle inconnue"));
        s.ouvrir();
        salles.sauvegarder(s);
    }

    @Override
    public void fermerSalle(CommandesOrganisation.FermerSalle commande) {
        Salle s = salles.trouver(commande.centreId(), commande.salleId()).orElseThrow(() -> new IllegalArgumentException("Salle inconnue"));
        s.fermer();
        salles.sauvegarder(s);
    }

    @Override
    public CreneauId creerCreneau(CommandesOrganisation.CreerCreneau commande) {
        CreneauId id = new CreneauId(UUID.randomUUID());
        Creneau c = new Creneau(id, commande.centreId(), commande.code(), commande.libelle());
        creneaux.sauvegarder(c);
        return id;
    }

    @Override
    public void modifierCreneau(CommandesOrganisation.ModifierCreneau commande) {
        Creneau c = creneaux.trouver(commande.centreId(), commande.id()).orElseThrow(() -> new IllegalArgumentException("Creneau inconnu"));
        c.modifier(commande.code(), commande.libelle());
        creneaux.sauvegarder(c);
    }

    @Override
    public List<Salle> listerSallesDuCentre(CentreId centreId) {
        return salles.listerParCentre(centreId);
    }

    @Override
    public List<Creneau> listerCreneaux(CentreId centreId) {
        return creneaux.listerParCentre(centreId);
    }
}
