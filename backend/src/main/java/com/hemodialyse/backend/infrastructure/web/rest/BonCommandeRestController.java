package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.domain.stock.model.LigneBonCommande;
import com.hemodialyse.backend.domain.stock.port.BonCommandeUseCase;
import com.hemodialyse.backend.infrastructure.web.dto.request.CenterActionRequest;
import com.hemodialyse.backend.infrastructure.web.dto.request.CreateBonCommandeRequest;
import com.hemodialyse.backend.infrastructure.web.dto.request.UpdateBonCommandeLignesRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/stock/bons-commande")
public class BonCommandeRestController {

    private final BonCommandeUseCase useCase;

    public BonCommandeRestController(BonCommandeUseCase useCase) {
        this.useCase = useCase;
    }

    @PreAuthorize("hasAnyRole('ADMIN','PHARMACIEN')")
    @PostMapping
    public ResponseEntity<?> create(@RequestBody @Valid CreateBonCommandeRequest req) {
        List<LigneBonCommande> lignes = mapLignes(req);
        return ResponseEntity.ok(useCase.create(CenterId.of(req.centerId()), req.fournisseurId(), lignes, req.userId()));
    }

    @PreAuthorize("hasAnyRole('ADMIN','PHARMACIEN')")
    @PutMapping("/{id}")
    public ResponseEntity<?> updateLignes(@PathVariable UUID id, @RequestBody @Valid UpdateBonCommandeLignesRequest req) {
        List<LigneBonCommande> lignes = req.lignes() == null ? List.of() : req.lignes().stream()
                .map(l -> new LigneBonCommande(l.id() != null ? l.id() : UUID.randomUUID(),
                        l.articleId(), l.quantite(), l.prixUnitaire()))
                .toList();
        return ResponseEntity.ok(useCase.updateLignes(CenterId.of(req.centerId()), id, lignes));
    }

    @PreAuthorize("hasAnyRole('ADMIN','PHARMACIEN')")
    @PostMapping("/{id}/valider")
    public ResponseEntity<?> valider(@PathVariable UUID id, @RequestBody @Valid CenterActionRequest req) {
        return ResponseEntity.ok(useCase.valider(CenterId.of(req.centerId()), id));
    }

    @PreAuthorize("hasAnyRole('ADMIN','PHARMACIEN','INFIRMIER')")
    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable UUID id, @RequestParam UUID centerId) {
        return ResponseEntity.ok(useCase.get(CenterId.of(centerId), id));
    }

    @PreAuthorize("hasAnyRole('ADMIN','PHARMACIEN','INFIRMIER')")
    @GetMapping
    public ResponseEntity<?> list(@RequestParam UUID centerId) {
        return ResponseEntity.ok(useCase.list(CenterId.of(centerId)));
    }

    private List<LigneBonCommande> mapLignes(CreateBonCommandeRequest req) {
        if (req.lignes() == null) {
            return List.of();
        }
        return req.lignes().stream()
                .map(l -> new LigneBonCommande(l.id() != null ? l.id() : UUID.randomUUID(),
                        l.articleId(), l.quantite(), l.prixUnitaire()))
                .toList();
    }
}

