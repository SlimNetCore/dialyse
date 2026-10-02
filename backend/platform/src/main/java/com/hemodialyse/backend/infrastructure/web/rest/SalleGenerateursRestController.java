package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.application.planning.SalleGenerateursService;
import com.hemodialyse.backend.domain.planning.model.SalleVue;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.infrastructure.security.CenterAccessGuard;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Vue d'ensemble des salles du centre : générateurs affectés, capacité et places restantes. Lecture seule, paginée
 * et bornée au centre.
 */
@RestController
@RequestMapping("/api/v1/planning/salles")
@PreAuthorize("hasAnyRole('ADMIN','SECRETAIRE')")
public class SalleGenerateursRestController {

    private final SalleGenerateursService service;
    private final CenterAccessGuard centerAccessGuard;

    public SalleGenerateursRestController(SalleGenerateursService service, CenterAccessGuard centerAccessGuard) {
        this.service = service;
        this.centerAccessGuard = centerAccessGuard;
    }

    @GetMapping
    public ResponseEntity<PagedResult<SalleResponse>> lister(@RequestParam(required = false) UUID centerId,
                                                             @RequestParam(defaultValue = "0") int page,
                                                             @RequestParam(defaultValue = "20") int size) {
        UUID centre = centerAccessGuard.requireCenter(centerId).value();
        PagedResult<SalleVue> paged = service.lister(centre, page, size);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(PagedResult.of(
                paged.items().stream().map(SalleResponse::de).toList(), paged.total(), paged.page(), paged.size()));
    }

    public record GenerateurResponse(UUID id, String code, String designation, String statut) {
    }

    public record SalleResponse(UUID id, String code, String nom, boolean isolement, Integer capacite,
                                int nbGenerateurs, Integer placesRestantes, boolean depassement,
                                List<GenerateurResponse> generateurs) {
        static SalleResponse de(SalleVue s) {
            return new SalleResponse(s.id(), s.code(), s.nom(), s.isolement(), s.capacite(), s.nbGenerateurs(),
                    s.placesRestantes(), s.depassement(), s.generateurs().stream()
                    .map(g -> new GenerateurResponse(g.id(), g.code(), g.designation(), g.statut())).toList());
        }
    }
}
