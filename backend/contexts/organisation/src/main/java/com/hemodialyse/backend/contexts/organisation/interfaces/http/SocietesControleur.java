package com.hemodialyse.backend.contexts.organisation.interfaces.http;

import com.hemodialyse.backend.contexts.organisation.application.CommandesOrganisation;
import com.hemodialyse.backend.contexts.organisation.application.OrganisationUseCases;
import com.hemodialyse.backend.contexts.organisation.domain.Centre;
import com.hemodialyse.backend.contexts.organisation.domain.LogoDeSociete;
import com.hemodialyse.backend.contexts.organisation.domain.Societe;
import com.hemodialyse.backend.contexts.organisation.interfaces.http.dto.CentreRequete;
import com.hemodialyse.backend.contexts.organisation.interfaces.http.dto.CreerSocieteRequete;
import com.hemodialyse.backend.contexts.organisation.interfaces.http.dto.SocieteReponse;
import com.hemodialyse.backend.contexts.organisation.interfaces.http.dto.SocieteRequete;
import com.hemodialyse.backend.contexts.organisation.interfaces.http.dto.TransfertCentreRequete;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult;
import com.hemodialyse.backend.contexts.sharedkernel.domain.SocieteId;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Sociétés et centres (slice O5, cutover legacy).
 * <p>
 * Sert à la fois les URLs canoniques ({@code /api/v1/organisation/societes}) et les URLs
 * historiques ({@code /api/v1/societes}) avec les mêmes DTOs et codes que le legacy :
 * le frontend continue de fonctionner sans changement pendant la bascule.
 * Réservé au SUPERADMIN, comme le legacy.
 * <p>
 * Les erreurs du domaine sont centralisées dans {@link ErreursDeLOrganisationAdvice} :
 * règles métier → 422, références inconnues → 404.
 */
@RestController
@RequestMapping({"/api/v1/organisation/societes", "/api/v1/societes"})
@PreAuthorize("hasRole('SUPERADMIN')")
public class SocietesControleur {

    private final OrganisationUseCases service;

    public SocietesControleur(OrganisationUseCases service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<PagedResult<SocieteReponse>> lister(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var resultat = service.listerSocietesParPage(q, page, size);
        Set<SocieteId> avecLogo = service.societesAvecLogo(
                resultat.items().stream().map(Societe::id).toList());
        List<SocieteReponse> items = resultat.items().stream()
                .map(s -> SocieteReponse.de(s,
                        service.listerCentres(s.id()), avecLogo.contains(s.id())))
                .toList();
        return ResponseEntity.ok(PagedResult.of(items, resultat.total(), resultat.page(), resultat.size()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SocieteReponse> consulter(@PathVariable UUID id) {
        return ResponseEntity.ok(toReponse(service.consulterSociete(new SocieteId(id))));
    }

    @PostMapping
    public ResponseEntity<SocieteReponse> creer(@RequestBody @Valid CreerSocieteRequete req) {
        SocieteId id = service.creerSociete(new CommandesOrganisation.CreerSociete(
                req.societe().code(), req.societe().raisonSociale(), req.societe().nif(),
                req.societe().nis(), req.societe().rc(), req.societe().coordonnees(),
                req.societe().piedDePage()));
        service.creerCentre(new CommandesOrganisation.CreerCentre(id, req.premierCentre().code(),
                req.premierCentre().nom(), req.premierCentre().coordonnees(), "UTC"));
        return ResponseEntity.status(HttpStatus.CREATED).body(toReponse(service.consulterSociete(id)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<SocieteReponse> modifier(@PathVariable UUID id,
                                                   @RequestBody @Valid SocieteRequete req) {
        Societe actuelle = service.consulterSociete(new SocieteId(id));
        boolean actif = req.actif() == null ? actuelle.actif() : req.actif();
        service.modifierSociete(new CommandesOrganisation.ModifierSociete(new SocieteId(id),
                req.code(), req.raisonSociale(), req.nif(), req.nis(), req.rc(),
                req.coordonnees(), req.piedDePage(), actif));
        return ResponseEntity.ok(toReponse(service.consulterSociete(new SocieteId(id))));
    }

    @PostMapping("/{id}/activer")
    public ResponseEntity<SocieteReponse> activer(@PathVariable UUID id) {
        return changerStatut(id, true);
    }

    @PostMapping("/{id}/desactiver")
    public ResponseEntity<SocieteReponse> desactiver(@PathVariable UUID id) {
        return changerStatut(id, false);
    }

    @PutMapping(value = "/{id}/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SocieteReponse> televerserLogo(@PathVariable UUID id,
                                                         @RequestParam("file") MultipartFile file) throws IOException {
        service.televerserLogoSociete(new SocieteId(id), file.getBytes());
        return ResponseEntity.ok(toReponse(service.consulterSociete(new SocieteId(id))));
    }

    @DeleteMapping("/{id}/logo")
    public ResponseEntity<Void> supprimerLogo(@PathVariable UUID id) {
        service.lireLogoSociete(new SocieteId(id));
        service.supprimerLogoSociete(new SocieteId(id));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/logos")
    public Set<UUID> societesAvecLogo(@RequestParam List<UUID> ids) {
        return service.societesAvecLogo(ids.stream().map(SocieteId::new).toList()).stream()
                .map(SocieteId::valeur).collect(Collectors.toSet());
    }

    @GetMapping("/{id}/logo")
    public ResponseEntity<byte[]> lireLogo(@PathVariable UUID id) {
        LogoDeSociete logo = service.lireLogoSociete(new SocieteId(id));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(logo.contentType()));
        headers.setCacheControl("no-store");
        headers.add("X-Content-Type-Options", "nosniff");
        headers.add("Content-Security-Policy", "default-src 'none'");
        return ResponseEntity.ok().headers(headers).body(logo.contenu());
    }

    @PostMapping("/{id}/centres")
    public ResponseEntity<SocieteReponse> ajouterCentre(@PathVariable UUID id,
                                                        @RequestBody @Valid CentreRequete req) {
        service.creerCentre(new CommandesOrganisation.CreerCentre(new SocieteId(id), req.code(),
                req.nom(), req.coordonnees(), "UTC"));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(toReponse(service.consulterSociete(new SocieteId(id))));
    }

    @PutMapping("/{id}/centres/{centreId}")
    public ResponseEntity<SocieteReponse> modifierCentre(@PathVariable UUID id,
                                                         @PathVariable UUID centreId,
                                                         @RequestBody @Valid CentreRequete req) {
        return ResponseEntity.ok(toReponse(modifierCentreAppartenant(id, centreId,
                new CommandesOrganisation.ModifierCentre(new CentreId(centreId),
                        req.code(), req.nom(), req.coordonnees()))));
    }

    @PostMapping("/{id}/centres/{centreId}/activer")
    public ResponseEntity<SocieteReponse> activerCentre(@PathVariable UUID id, @PathVariable UUID centreId) {
        return changerStatutCentre(id, centreId, true);
    }

    @PostMapping("/{id}/centres/{centreId}/desactiver")
    public ResponseEntity<SocieteReponse> desactiverCentre(@PathVariable UUID id, @PathVariable UUID centreId) {
        return changerStatutCentre(id, centreId, false);
    }

    @PostMapping("/{id}/centres/{centreId}/transfert")
    public ResponseEntity<Void> transfererCentre(@PathVariable UUID id, @PathVariable UUID centreId,
                                                 @RequestBody @Valid TransfertCentreRequete req) {
        Centre centre = centreAppartenant(id, centreId);
        service.transfererCentre(new CommandesOrganisation.TransfererCentre(centre.id(),
                new SocieteId(req.societeCibleId())));
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<SocieteReponse> changerStatut(UUID id, boolean actif) {
        Societe societe = service.consulterSociete(new SocieteId(id));
        service.modifierSociete(new CommandesOrganisation.ModifierSociete(societe.id(),
                societe.code(), societe.raisonSociale(), societe.nif(), societe.nis(),
                societe.rc(), societe.coordonnees(), societe.piedDePage(), actif));
        return ResponseEntity.ok(toReponse(service.consulterSociete(new SocieteId(id))));
    }

    private ResponseEntity<SocieteReponse> changerStatutCentre(UUID id, UUID centreId, boolean actif) {
        Centre centre = centreAppartenant(id, centreId);
        if (actif) {
            service.activerCentre(centre.id());
        } else {
            service.desactiverCentre(centre.id());
        }
        return ResponseEntity.ok(toReponse(service.consulterSociete(new SocieteId(id))));
    }

    private Societe modifierCentreAppartenant(UUID id, UUID centreId,
                                              CommandesOrganisation.ModifierCentre commande) {
        Centre centre = centreAppartenant(id, centreId);
        service.modifierCentre(commande);
        return service.consulterSociete(new SocieteId(id));
    }

    private Centre centreAppartenant(UUID id, UUID centreId) {
        Centre centre = service.lireParametresCentre(new CentreId(centreId));
        if (!centre.societeId().valeur().equals(id)) {
            throw new IllegalArgumentException("Centre introuvable dans cette societe");
        }
        return centre;
    }

    private SocieteReponse toReponse(Societe societe) {
        boolean logo = !service.societesAvecLogo(List.of(societe.id())).isEmpty();
        return SocieteReponse.de(societe, service.listerCentres(societe.id()), logo);
    }
}
