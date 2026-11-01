package com.hemodialyse.backend.contexts.organisation.interfaces.http;

import com.hemodialyse.backend.contexts.organisation.application.CommandesOrganisation;
import com.hemodialyse.backend.contexts.organisation.application.OrganisationUseCases;
import com.hemodialyse.backend.contexts.organisation.domain.LogoDeSociete;
import com.hemodialyse.backend.contexts.organisation.domain.LogoRefuse;
import com.hemodialyse.backend.contexts.organisation.domain.Societe;
import com.hemodialyse.backend.contexts.sharedkernel.domain.SocieteId;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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
    public com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult<Societe> lister(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.listerSocietesParPage(q, page, size);
    }

    @GetMapping("/{id}")
    public Societe consulter(@PathVariable UUID id) {
        return service.consulterSociete(new SocieteId(id));
    }

    @PutMapping(value = "/{id}/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> televerserLogo(@PathVariable UUID id, @RequestParam("file") MultipartFile file) {
        try {
            service.televerserLogoSociete(new SocieteId(id), file.getBytes());
        } catch (LogoRefuse refuse) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT)
                    .body(Map.of("violations", List.of(
                            Map.of("code", refuse.code(), "detail", refuse.detail()))));
        } catch (IOException e) {
            throw new IllegalStateException("Lecture du fichier impossible", e);
        }
        return ResponseEntity.ok(Map.of("id", id));
    }

    @GetMapping("/{id}/logo")
    public ResponseEntity<byte[]> lireLogo(@PathVariable UUID id) {
        try {
            LogoDeSociete logo = service.lireLogoSociete(new SocieteId(id));
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(logo.contentType()))
                    .body(logo.contenu());
        } catch (IllegalArgumentException inconnue) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}/logo")
    public ResponseEntity<Void> supprimerLogo(@PathVariable UUID id) {
        service.supprimerLogoSociete(new SocieteId(id));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/logos")
    public Set<UUID> societesAvecLogo(@RequestParam List<UUID> ids) {
        return service.societesAvecLogo(ids.stream().map(SocieteId::new).collect(Collectors.toSet()))
                .stream().map(SocieteId::valeur).collect(Collectors.toSet());
    }

    public record CreerSocieteRequete(String code, String raisonSociale) {
    }

    public record ModifierSocieteRequete(String code, String raisonSociale, boolean actif) {
    }
}
