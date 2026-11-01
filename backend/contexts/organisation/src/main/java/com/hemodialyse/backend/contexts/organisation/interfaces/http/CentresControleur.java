package com.hemodialyse.backend.contexts.organisation.interfaces.http;

import com.hemodialyse.backend.contexts.organisation.application.CommandesOrganisation;
import com.hemodialyse.backend.contexts.organisation.application.OrganisationUseCases;
import com.hemodialyse.backend.contexts.organisation.domain.Centre;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.SocieteId;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/organisation/centres")
public class CentresControleur {
    private final OrganisationUseCases service;

    public CentresControleur(OrganisationUseCases service) {
        this.service = service;
    }

    @PostMapping
    public UUID creer(@RequestBody CreerCentreRequete req) {
        return service.creerCentre(new CommandesOrganisation.CreerCentre(new SocieteId(req.societeId()), req.code(), req.nom(), req.fuseauHoraire(), req.capaciteTheorique())).valeur();
    }

    @PutMapping("/{id}")
    public void modifier(@PathVariable UUID id, @RequestBody ModifierCentreRequete req) {
        service.modifierCentre(new CommandesOrganisation.ModifierCentre(new CentreId(id), req.nom(), req.fuseauHoraire(), req.capaciteTheorique(), req.actif()));
    }

    @PostMapping("/{id}/transferer")
    public void transferer(@PathVariable UUID id, @RequestBody TransfererCentreRequete req) {
        service.transfererCentre(new CommandesOrganisation.TransfererCentre(new CentreId(id), new SocieteId(req.nouvelleSocieteId())));
    }

    @GetMapping("/actifs")
    public List<Centre> actifs(@RequestParam UUID societeId) {
        return service.listerCentresActifs(new SocieteId(societeId));
    }

    @GetMapping
    public List<Centre> lister(@RequestParam UUID societeId) {
        return service.listerCentres(new SocieteId(societeId));
    }

    @GetMapping("/{id}/parametres")
    public Centre parametres(@PathVariable UUID id) {
        return service.lireParametresCentre(new CentreId(id));
    }

    public record CreerCentreRequete(UUID societeId, String code, String nom, String fuseauHoraire,
                                     int capaciteTheorique) {
    }

    public record ModifierCentreRequete(String nom, String fuseauHoraire, int capaciteTheorique, boolean actif) {
    }

    public record TransfererCentreRequete(UUID nouvelleSocieteId) {
    }
}
