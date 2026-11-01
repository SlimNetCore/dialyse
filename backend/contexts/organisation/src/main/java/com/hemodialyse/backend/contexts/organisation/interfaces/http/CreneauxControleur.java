package com.hemodialyse.backend.contexts.organisation.interfaces.http;

import com.hemodialyse.backend.contexts.organisation.application.CommandesOrganisation;
import com.hemodialyse.backend.contexts.organisation.application.OrganisationUseCases;
import com.hemodialyse.backend.contexts.organisation.domain.Creneau;
import com.hemodialyse.backend.contexts.organisation.domain.CreneauId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/organisation/creneaux")
public class CreneauxControleur {
    private final OrganisationUseCases service;

    public CreneauxControleur(OrganisationUseCases service) {
        this.service = service;
    }

    @PostMapping
    public UUID creer(@RequestBody CreerCreneauRequete req) {
        return service.creerCreneau(new CommandesOrganisation.CreerCreneau(new CentreId(req.centreId()), req.code(), req.libelle())).valeur();
    }

    @PutMapping("/{id}")
    public void modifier(@PathVariable UUID id, @RequestBody ModifierCreneauRequete req) {
        service.modifierCreneau(new CommandesOrganisation.ModifierCreneau(new CentreId(req.centreId()), new CreneauId(id), req.code(), req.libelle()));
    }

    @GetMapping
    public List<Creneau> lister(@RequestParam UUID centreId) {
        return service.listerCreneaux(new CentreId(centreId));
    }

    public record CreerCreneauRequete(UUID centreId, String code, String libelle) {
    }

    public record ModifierCreneauRequete(UUID centreId, String code, String libelle) {
    }
}
