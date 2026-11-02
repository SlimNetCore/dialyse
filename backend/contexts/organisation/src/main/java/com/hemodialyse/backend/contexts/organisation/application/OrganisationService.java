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
    private final UniteDeTravail unite;
    private final com.hemodialyse.backend.contexts.organisation.domain.VerrouDeLaSociete verrou;

    private final java.time.Clock horloge;
    private final GenerateurDIdentifiants identifiants;

    public OrganisationService(SocieteRepository societes, CentreRepository centres, SalleRepository salles, CreneauRepository creneaux,
                               UniteDeTravail unite, com.hemodialyse.backend.contexts.organisation.domain.VerrouDeLaSociete verrou,
                               java.time.Clock horloge, GenerateurDIdentifiants identifiants) {
        this.societes = societes;
        this.centres = centres;
        this.salles = salles;
        this.creneaux = creneaux;
        this.unite = unite;
        this.verrou = verrou;
        this.horloge = horloge;
        this.identifiants = identifiants;
    }

    @Override
    public SocieteId creerSociete(CommandesOrganisation.CreerSociete commande) {
        garantirCodeSocieteLibre(commande.code(), null);
        SocieteId id = new SocieteId(identifiants.suivant());
        Societe s = new Societe(id, commande.code(), commande.raisonSociale(), commande.nif(),
                commande.nis(), commande.rc(), commande.coordonnees(), commande.piedDePage(), true,
                OffsetDateTime.now(horloge).withOffsetSameInstant(ZoneOffset.UTC));
        societes.sauvegarder(s);
        return id;
    }

    @Override
    public void modifierSociete(CommandesOrganisation.ModifierSociete commande) {
        Societe s = societes.trouver(commande.id()).orElseThrow(com.hemodialyse.backend.contexts.organisation.domain.SocieteIntrouvable::new);
        garantirCodeSocieteLibre(commande.code(), commande.id());
        s.modifier(commande.code(), commande.raisonSociale(), commande.nif(), commande.nis(),
                commande.rc(), commande.coordonnees(), commande.piedDePage(), commande.actif());
        societes.sauvegarder(s);
    }

    @Override
    public CentreId creerCentre(CommandesOrganisation.CreerCentre commande) {
        if (societes.trouver(commande.societeId()).isEmpty()) throw new com.hemodialyse.backend.contexts.organisation.domain.SocieteIntrouvable();
        garantirCodeCentreLibre(commande.code(), null);
        CentreId id = new CentreId(identifiants.suivant());
        Centre c = new Centre(id, commande.societeId(), commande.code(), commande.nom(),
                commande.coordonnees(), commande.fuseauHoraire(), true);
        centres.sauvegarder(c);
        return id;
    }

    @Override
    public void modifierCentre(CommandesOrganisation.ModifierCentre commande) {
        Centre c = centres.trouver(commande.id()).orElseThrow(com.hemodialyse.backend.contexts.organisation.domain.CentreIntrouvable::new);
        garantirCodeCentreLibre(commande.code(), commande.id());
        c.modifier(commande.code(), commande.nom(), commande.coordonnees());
        centres.sauvegarder(c);
    }

    @Override
    public void activerCentre(CentreId centreId) {
        Centre c = centres.trouver(centreId).orElseThrow(com.hemodialyse.backend.contexts.organisation.domain.CentreIntrouvable::new);
        c.activer();
        centres.sauvegarder(c);
    }

    /**
     * Invariant « au moins un centre actif par société » : la société est verrouillée AVANT de lire ses centres actifs, dans la
     * même transaction que l'écriture. Deux désactivations concurrentes ne passent donc pas toutes les deux (la seconde attend,
     * relit, puis est refusée ; au-delà du délai de verrou : 409).
     */
    @Override
    public void desactiverCentre(CentreId centreId) {
        unite.executer(() -> {
            Centre lu = centres.trouver(centreId).orElseThrow(com.hemodialyse.backend.contexts.organisation.domain.CentreIntrouvable::new);
            verrou.prendre(lu.societeId());
            Centre c = centres.trouver(centreId).orElseThrow(com.hemodialyse.backend.contexts.organisation.domain.CentreIntrouvable::new);
            c.desactiver(centres.listerActifs(c.societeId()));
            centres.sauvegarder(c);
            return null;
        });
    }

    @Override
    public void transfererCentre(CommandesOrganisation.TransfererCentre commande) {
        unite.executer(() -> {
            Centre lu = centres.trouver(commande.id()).orElseThrow(com.hemodialyse.backend.contexts.organisation.domain.CentreIntrouvable::new);
            // Les deux sociétés sont verrouillées, dans l'ordre de leurs identifiants (pas d'interblocage) : la cible ne peut pas
            // être désactivée ni modifiée en parallèle.
            SocieteId origine = lu.societeId();
            SocieteId cibleId = commande.nouvelleSociete();
            boolean origineDabord = origine.valeur().compareTo(cibleId.valeur()) <= 0;
            verrou.prendre(origineDabord ? origine : cibleId);
            if (!origine.equals(cibleId)) verrou.prendre(origineDabord ? cibleId : origine);
            Centre c = centres.trouver(commande.id()).orElseThrow(com.hemodialyse.backend.contexts.organisation.domain.CentreIntrouvable::new);
            Societe cible = societes.trouver(commande.nouvelleSociete())
                    .orElseThrow(com.hemodialyse.backend.contexts.organisation.domain.SocieteIntrouvable::new);
            c.transfererVers(cible, centres.listerActifs(c.societeId()));
            centres.sauvegarder(c);
            return null;
        });
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

    @Override
    public List<Centre> listerCentresActifs(SocieteId societeId) {
        return centres.listerActifs(societeId);
    }

    @Override
    public Centre lireParametresCentre(CentreId centreId) {
        return centres.trouver(centreId).orElseThrow(com.hemodialyse.backend.contexts.organisation.domain.CentreIntrouvable::new);
    }

    @Override
    public List<Societe> listerSocietes() {
        return societes.lister();
    }

    @Override
    public com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult<Societe> listerSocietesParPage(
            String recherche, int page, int size) {
        // RG-ORG-008 : page positive, taille entre 1 et 100.
        return societes.listerParPage(recherche, Math.max(page, 0), Math.min(Math.max(size, 1), 100));
    }

    @Override
    public Societe consulterSociete(SocieteId societeId) {
        return societes.trouver(societeId).orElseThrow(com.hemodialyse.backend.contexts.organisation.domain.SocieteIntrouvable::new);
    }

    @Override
    public List<Centre> listerCentres(SocieteId societeId) {
        return centres.listerParSociete(societeId);
    }

    @Override
    public LogoDeSociete lireLogoSociete(SocieteId societeId) {
        return societes.lireLogo(societeId)
                .orElseThrow(com.hemodialyse.backend.contexts.organisation.domain.LogoIntrouvable::new);
    }

    @Override
    public void televerserLogoSociete(SocieteId societeId, byte[] contenu) {
        if (societes.trouver(societeId).isEmpty()) throw new com.hemodialyse.backend.contexts.organisation.domain.SocieteIntrouvable();
        LogoDeSociete logo = LogoDeSociete.valider(contenu);
        ValidateurImage.verifierDecodable(contenu);
        societes.enregistrerLogo(societeId, logo);
    }

    @Override
    public void supprimerLogoSociete(SocieteId societeId) {
        if (societes.trouver(societeId).isEmpty()) throw new com.hemodialyse.backend.contexts.organisation.domain.SocieteIntrouvable();
        societes.supprimerLogo(societeId);
    }

    @Override
    public java.util.Set<SocieteId> societesAvecLogo(java.util.Collection<SocieteId> ids) {
        return societes.idsAvecLogo(ids);
    }

    @Override
    public void ouvrirSalle(CommandesOrganisation.OuvrirSalle commande) {
        Salle s = salles.trouver(commande.centreId(), commande.salleId()).orElseThrow(() -> new com.hemodialyse.backend.contexts.organisation.domain.ElementIntrouvable("Salle inconnue"));
        s.ouvrir();
        salles.sauvegarder(s);
    }

    @Override
    public void fermerSalle(CommandesOrganisation.FermerSalle commande) {
        Salle s = salles.trouver(commande.centreId(), commande.salleId()).orElseThrow(() -> new com.hemodialyse.backend.contexts.organisation.domain.ElementIntrouvable("Salle inconnue"));
        s.fermer();
        salles.sauvegarder(s);
    }

    @Override
    public CreneauId creerCreneau(CommandesOrganisation.CreerCreneau commande) {
        CreneauId id = new CreneauId(identifiants.suivant());
        Creneau c = new Creneau(id, commande.centreId(), commande.code(), commande.libelle());
        creneaux.sauvegarder(c);
        return id;
    }

    @Override
    public void modifierCreneau(CommandesOrganisation.ModifierCreneau commande) {
        Creneau c = creneaux.trouver(commande.centreId(), commande.id()).orElseThrow(() -> new com.hemodialyse.backend.contexts.organisation.domain.ElementIntrouvable("Creneau inconnu"));
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
