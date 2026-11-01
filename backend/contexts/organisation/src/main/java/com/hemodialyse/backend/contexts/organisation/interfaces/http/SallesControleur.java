package com.hemodialyse.backend.contexts.organisation.interfaces.http;

import com.hemodialyse.backend.contexts.organisation.application.CommandesOrganisation;
import com.hemodialyse.backend.contexts.organisation.application.OrganisationUseCases;
import com.hemodialyse.backend.contexts.organisation.domain.Salle;
import com.hemodialyse.backend.contexts.organisation.domain.SalleId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController("organisationSallesControleur")
@RequestMapping("/api/v1/organisation/salles")
public class SallesControleur {
    private final OrganisationUseCases service;

    public SallesControleur(OrganisationUseCases service) {
        this.service = service;
    }

    @PostMapping("/{id}/ouvrir")
    public void ouvrir(@PathVariable UUID id, @RequestParam UUID centreId) {
        service.ouvrirSalle(new CommandesOrganisation.OuvrirSalle(new CentreId(centreId), new SalleId(id)));
    }

    @PostMapping("/{id}/fermer")
    public void fermer(@PathVariable UUID id, @RequestParam UUID centreId) {
        service.fermerSalle(new CommandesOrganisation.FermerSalle(new CentreId(centreId), new SalleId(id)));
    }

    @GetMapping
    public List<Salle> lister(@RequestParam UUID centreId) {
        return service.listerSallesDuCentre(new CentreId(centreId));
    }
}
