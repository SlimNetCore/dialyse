package com.hemodialyse.backend.contexts.organisation.application;

import com.hemodialyse.backend.contexts.organisation.domain.Centre;
import com.hemodialyse.backend.contexts.organisation.domain.CentreRepository;
import com.hemodialyse.backend.contexts.organisation.domain.Creneau;
import com.hemodialyse.backend.contexts.organisation.domain.CreneauId;
import com.hemodialyse.backend.contexts.organisation.domain.CreneauRepository;
import com.hemodialyse.backend.contexts.organisation.domain.LogoDeSociete;
import com.hemodialyse.backend.contexts.organisation.domain.Salle;
import com.hemodialyse.backend.contexts.organisation.domain.SalleId;
import com.hemodialyse.backend.contexts.organisation.domain.SalleRepository;
import com.hemodialyse.backend.contexts.organisation.domain.Societe;
import com.hemodialyse.backend.contexts.organisation.domain.SocieteRepository;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.SocieteId;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
        garantirCodeSocieteLibre(commande.code(), null);
        SocieteId id = new SocieteId(UUID.randomUUID());
        Societe s = new Societe(id, commande.code(), commande.raisonSociale(), commande.nif(),
                commande.nis(), commande.rc(), commande.coordonnees(), commande.piedDePage(), true,
                OffsetDateTime.now(ZoneOffset.UTC));
        societes.sauvegarder(s);
        return id;
    }

    @Override
    public void modifierSociete(CommandesOrganisation.ModifierSociete commande) {
        Societe s = societes.trouver(commande.id()).orElseThrow(() -> new IllegalArgumentException("Societe inconnue"));
        garantirCodeSocieteLibre(commande.code(), commande.id());
        s.modifier(commande.code(), commande.raisonSociale(), commande.nif(), commande.nis(),
                commande.rc(), commande.coordonnees(), commande.piedDePage(), commande.actif());
        societes.sauvegarder(s);
    }

    @Override
    public CentreId creerCentre(CommandesOrganisation.CreerCentre commande) {
        if (societes.trouver(commande.societeId()).isEmpty()) throw new IllegalArgumentException("Societe inconnue");
        garantirCodeCentreLibre(commande.code(), null);
        CentreId id = new CentreId(UUID.randomUUID());
        Centre c = new Centre(id, commande.societeId(), commande.code(), commande.nom(),
                commande.coordonnees(), commande.fuseauHoraire(), true);
        centres.sauvegarder(c);
        return id;
    }

    @Override
    public void modifierCentre(CommandesOrganisation.ModifierCentre commande) {
        Centre c = centres.trouver(commande.id()).orElseThrow(() -> new IllegalArgumentException("Centre inconnu"));
        garantirCodeCentreLibre(commande.code(), commande.id());
        if (!commande.actif() && c.actif() && estDernierCentreActif(c)) {
            throw new IllegalArgumentException("SOCIETE_DERNIER_CENTRE_ACTIF");
        }
        c.modifierParametres(commande.code(), commande.nom(), commande.coordonnees(), commande.fuseauHoraire(), commande.actif());
        centres.sauvegarder(c);
    }

    @Override
    public void transfererCentre(CommandesOrganisation.TransfererCentre commande) {
        Centre c = centres.trouver(commande.id()).orElseThrow(() -> new IllegalArgumentException("Centre inconnu"));
        if (societes.trouver(commande.nouvelleSociete()).isEmpty())
            throw new IllegalArgumentException("Societe inconnue");
        if (c.actif() && estDernierCentreActif(c)) {
            throw new IllegalArgumentException("SOCIETE_DERNIER_CENTRE_ACTIF");
        }
        c.transferer(commande.nouvelleSociete());
        centres.sauvegarder(c);
    }

    private void garantirCodeSocieteLibre(String code, SocieteId exclu) {
        if (code == null || code.isBlank()) return;
        societes.trouverParCode(code.trim()).ifPresent(existante -> {
            if (!existante.id().equals(exclu)) {
                throw new IllegalArgumentException("SOCIETE_CODE_DEJA_UTILISE");
            }
        });
    }

    private void garantirCodeCentreLibre(String code, CentreId exclu) {
        if (code == null || code.isBlank()) return;
        centres.trouverParCode(code.trim()).ifPresent(existant -> {
            if (!existant.id().equals(exclu)) {
                throw new IllegalArgumentException("CENTRE_CODE_DEJA_UTILISE");
            }
        });
    }

    private boolean estDernierCentreActif(Centre centre) {
        return centres.listerActifs(centre.societeId()).stream()
                .allMatch(a -> a.id().equals(centre.id()));
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
    public com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult<Societe> listerSocietesParPage(
            String recherche, int page, int size) {
        // RG-ORG-008 : taille plafonnée à 100.
        return societes.listerParPage(recherche, page, Math.min(size, 100));
    }

    @Override
    public Societe consulterSociete(SocieteId societeId) {
        return societes.trouver(societeId).orElseThrow(() -> new IllegalArgumentException("Societe inconnue"));
    }

    @Override
    public List<Centre> listerCentres(SocieteId societeId) {
        return centres.listerParSociete(societeId);
    }

    @Override
    public LogoDeSociete lireLogoSociete(SocieteId societeId) {
        return societes.lireLogo(societeId)
                .orElseThrow(() -> new IllegalArgumentException("Logo introuvable"));
    }

    @Override
    public void televerserLogoSociete(SocieteId societeId, byte[] contenu) {
        if (societes.trouver(societeId).isEmpty()) throw new IllegalArgumentException("Societe inconnue");
        LogoDeSociete logo = LogoDeSociete.valider(contenu);
        ValidateurImage.verifierDecodable(contenu);
        societes.enregistrerLogo(societeId, logo);
    }

    @Override
    public void supprimerLogoSociete(SocieteId societeId) {
        if (societes.trouver(societeId).isEmpty()) throw new IllegalArgumentException("Societe inconnue");
        societes.supprimerLogo(societeId);
    }

    @Override
    public java.util.Set<SocieteId> societesAvecLogo(java.util.Collection<SocieteId> ids) {
        return societes.idsAvecLogo(ids);
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
