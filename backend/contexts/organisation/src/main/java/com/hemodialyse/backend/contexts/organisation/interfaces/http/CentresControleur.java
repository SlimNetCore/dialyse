package com.hemodialyse.backend.contexts.organisation.interfaces.http;

import com.hemodialyse.backend.contexts.organisation.application.CommandesOrganisation;
import com.hemodialyse.backend.contexts.organisation.application.OrganisationUseCases;
import com.hemodialyse.backend.contexts.organisation.application.SessionCourante;
import com.hemodialyse.backend.contexts.organisation.domain.Centre;
import com.hemodialyse.backend.contexts.organisation.interfaces.http.dto.CreerCentreRequete;
import com.hemodialyse.backend.contexts.organisation.interfaces.http.dto.ModifierCentreRequete;
import com.hemodialyse.backend.contexts.organisation.interfaces.http.dto.TransfererCentreRequete;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.SocieteId;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

/**
 * Centres (O1). Droits : créer, modifier, transférer et lister les centres d'une société sont réservés au SUPERADMIN, comme la
 * gestion des centres de {@code /api/v1/societes} dans le legacy (route nouvelle : aucun équivalent legacy direct) ; la lecture
 * des paramètres d'un centre est bornée au centre de la session. Le corps n'est lu qu'après la vérification des droits.
 */
@RestController
@RequestMapping("/api/v1/organisation/centres")
public class CentresControleur {
    private final OrganisationUseCases service;
    private final SessionCourante session;
    private final ObjectMapper lecteur;

    public CentresControleur(OrganisationUseCases service, SessionCourante session, ObjectMapper lecteur) {
        this.service = service;
        this.session = session;
        this.lecteur = lecteur;
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPERADMIN')")
    public UUID creer(HttpServletRequest requete, @RequestBody(required = false) byte[] corps)
            throws HttpMediaTypeNotSupportedException {
        CreerCentreRequete req = CorpsJson.lire(lecteur, requete, corps, CreerCentreRequete.class);
        return service.creerCentre(new CommandesOrganisation.CreerCentre(new SocieteId(req.societeId()),
                req.code(), req.nom(), req.coordonnees(), req.fuseauHoraire())).valeur();
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public void modifier(@PathVariable UUID id, HttpServletRequest requete, @RequestBody(required = false) byte[] corps)
            throws HttpMediaTypeNotSupportedException {
        ModifierCentreRequete req = CorpsJson.lire(lecteur, requete, corps, ModifierCentreRequete.class);
        service.modifierCentre(new CommandesOrganisation.ModifierCentre(new CentreId(id), req.code(),
                req.nom(), req.coordonnees()));
    }

    @PostMapping("/{id}/transferer")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public void transferer(@PathVariable UUID id, HttpServletRequest requete, @RequestBody(required = false) byte[] corps)
            throws HttpMediaTypeNotSupportedException {
        TransfererCentreRequete req = CorpsJson.lire(lecteur, requete, corps, TransfererCentreRequete.class);
        service.transfererCentre(new CommandesOrganisation.TransfererCentre(new CentreId(id), new SocieteId(req.nouvelleSocieteId())));
    }

    @GetMapping("/actifs")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public List<Centre> actifs(@RequestParam UUID societeId) {
        return service.listerCentresActifs(new SocieteId(societeId));
    }

    @GetMapping
    @PreAuthorize("hasRole('SUPERADMIN')")
    public List<Centre> lister(@RequestParam UUID societeId) {
        return service.listerCentres(new SocieteId(societeId));
    }

    @GetMapping("/{id}/parametres")
    public Centre parametres(@PathVariable UUID id) {
        CentreId centre = session.centre(id);
        return service.lireParametresCentre(centre);
    }
}
