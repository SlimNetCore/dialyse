package com.hemodialyse.backend.contexts.organisation.interfaces.http;

import com.hemodialyse.backend.contexts.organisation.application.CommandesOrganisation;
import com.hemodialyse.backend.contexts.organisation.application.OrganisationUseCases;
import com.hemodialyse.backend.contexts.organisation.domain.Societe;
import com.hemodialyse.backend.contexts.sharedkernel.domain.SocieteId;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/organisation/societes")
public class SocietesControleur {
    private final OrganisationUseCases service;

    public SocietesControleur(OrganisationUseCases service) {
        this.service = service;
    }

    @PostMapping
    public UUID creer(@RequestBody CreerSocieteRequete req) {
        return service.creerSociete(new CommandesOrganisation.CreerSociete(req.code(), req.raisonSociale())).valeur();
    }

    @PutMapping("/{id}")
    public void modifier(@PathVariable UUID id, @RequestBody ModifierSocieteRequete req) {
        service.modifierSociete(new CommandesOrganisation.ModifierSociete(new SocieteId(id), req.code(), req.raisonSociale(), req.actif()));
    }

    @GetMapping
    public List<Societe> lister() {
        return service.listerSocietes();
    }

    public record CreerSocieteRequete(String code, String raisonSociale) {
    }

    public record ModifierSocieteRequete(String code, String raisonSociale, boolean actif) {
    }
}
