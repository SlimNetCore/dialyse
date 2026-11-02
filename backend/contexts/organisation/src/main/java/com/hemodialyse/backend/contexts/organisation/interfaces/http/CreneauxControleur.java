package com.hemodialyse.backend.contexts.organisation.interfaces.http;

import com.hemodialyse.backend.contexts.organisation.application.CommandesOrganisation;
import com.hemodialyse.backend.contexts.organisation.application.OrganisationUseCases;
import com.hemodialyse.backend.contexts.organisation.application.SessionCourante;
import com.hemodialyse.backend.contexts.organisation.domain.Creneau;
import com.hemodialyse.backend.contexts.organisation.domain.CreneauId;
import com.hemodialyse.backend.contexts.organisation.interfaces.http.dto.CreerCreneauRequete;
import com.hemodialyse.backend.contexts.organisation.interfaces.http.dto.ModifierCreneauRequete;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

/**
 * Créneaux (O2). Créer et modifier : ADMIN (son centre) ou SUPERADMIN, comme l'administration des référentiels du legacy
 * (route nouvelle sans équivalent direct) ; lecture : tout utilisateur authentifié, bornée au centre de la session. Le corps
 * n'est lu qu'après la vérification des droits ; le centre du corps est confronté à la session.
 */
@RestController
@RequestMapping("/api/v1/organisation/creneaux")
public class CreneauxControleur {
    private final OrganisationUseCases service;
    private final SessionCourante session;
    private final ObjectMapper lecteur;

    public CreneauxControleur(OrganisationUseCases service, SessionCourante session, ObjectMapper lecteur) {
        this.service = service;
        this.session = session;
        this.lecteur = lecteur;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPERADMIN')")
    public UUID creer(HttpServletRequest requete, @RequestBody(required = false) byte[] corps)
            throws HttpMediaTypeNotSupportedException {
        CreerCreneauRequete req = CorpsJson.lire(lecteur, requete, corps, CreerCreneauRequete.class);
        CentreId centre = session.centre(req.centreId());
        return service.creerCreneau(new CommandesOrganisation.CreerCreneau(centre, req.code(), req.libelle())).valeur();
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERADMIN')")
    public void modifier(@PathVariable UUID id, HttpServletRequest requete, @RequestBody(required = false) byte[] corps)
            throws HttpMediaTypeNotSupportedException {
        ModifierCreneauRequete req = CorpsJson.lire(lecteur, requete, corps, ModifierCreneauRequete.class);
        CentreId centre = session.centre(req.centreId());
        service.modifierCreneau(new CommandesOrganisation.ModifierCreneau(centre, new CreneauId(id), req.code(), req.libelle()));
    }

    @GetMapping
    public List<Creneau> lister(@RequestParam(required = false) UUID centreId) {
        return service.listerCreneaux(session.centre(centreId));
    }
}
