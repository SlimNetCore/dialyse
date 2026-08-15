package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.application.facturation.TypeTvaApplicationService;
import com.hemodialyse.backend.domain.facturation.aggregate.TypeTVA;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.infrastructure.web.dto.request.TvaTypeRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tva-types")
public class TvaTypesRestController {

    private final TypeTvaApplicationService service;

    public TvaTypesRestController(TypeTvaApplicationService service) {
        this.service = service;
    }

    @PreAuthorize("hasAnyRole('ADMIN','MEDECIN','SECRETAIRE')")
    @GetMapping
    public ResponseEntity<PagedResult<TypeTVA>> list(
            @RequestParam UUID centerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(service.listerTypesTva(centerId, page, size));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<TypeTVA> create(@RequestBody @Valid TvaTypeRequest request) {
        TypeTVA created = service.creerTypeTva(
                request.centerId(), request.libelle(), request.taux(), request.typePrestation(),
                request.exonere(), request.dateDebutValidite(), request.dateFinValidite(),
                request.texteReference(), request.userId()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<TypeTVA> update(@PathVariable UUID id, @RequestBody @Valid TvaTypeRequest request) {
        TypeTVA updated = service.modifierTypeTva(
                id, request.centerId(), request.libelle(), request.taux(), request.typePrestation(),
                request.exonere(), request.dateDebutValidite(), request.dateFinValidite(),
                request.texteReference(), request.userId()
        );
        return ResponseEntity.ok(updated);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable UUID id, @RequestParam UUID centerId) {
        service.desactiverTypeTva(id, centerId);
        return ResponseEntity.noContent().build();
    }
}


