package com.hemodialyse.backend.contexts.organisation.interfaces.http;

import com.hemodialyse.backend.contexts.organisation.application.CommandesOrganisation;
import com.hemodialyse.backend.contexts.organisation.application.OrganisationUseCases;
import com.hemodialyse.backend.contexts.organisation.application.SessionCourante;
import com.hemodialyse.backend.contexts.organisation.interfaces.http.dto.ReponsesDuReferentiel;
import com.hemodialyse.backend.contexts.organisation.domain.SalleId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Salles (O2). Ouvrir et fermer : ADMIN (son centre) ou SUPERADMIN, comme l'administration des référentiels du legacy
 * ({@code /api/v1/admin/referentials}, route nouvelle sans équivalent direct) ; lecture : tout utilisateur authentifié,
 * bornée au centre de la session. Le centre retenu vient de la session, jamais seulement du client.
 */
@RestController("organisationSallesControleur")
@RequestMapping("/api/v1/organisation/salles")
public class SallesControleur {
    private final OrganisationUseCases service;
    private final SessionCourante session;

    public SallesControleur(OrganisationUseCases service, SessionCourante session) {
        this.service = service;
        this.session = session;
    }

    @PostMapping("/{id}/ouvrir")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERADMIN')")
    public void ouvrir(@PathVariable UUID id, @RequestParam(required = false) UUID centreId) {
        CentreId centre = session.centre(centreId);
        service.ouvrirSalle(new CommandesOrganisation.OuvrirSalle(centre, new SalleId(id)));
    }

    @PostMapping("/{id}/fermer")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERADMIN')")
    public void fermer(@PathVariable UUID id, @RequestParam(required = false) UUID centreId) {
        CentreId centre = session.centre(centreId);
        service.fermerSalle(new CommandesOrganisation.FermerSalle(centre, new SalleId(id)));
    }

    @GetMapping
    public List<ReponsesDuReferentiel.SalleReponse> lister(@RequestParam(required = false) UUID centreId) {
        return ReponsesDuReferentiel.liste(service.listerSallesDuCentre(session.centre(centreId)), ReponsesDuReferentiel.SalleReponse::de);
    }
}
