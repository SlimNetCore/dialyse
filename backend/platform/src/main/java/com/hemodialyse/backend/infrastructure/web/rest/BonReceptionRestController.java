package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.domain.stock.model.LigneReception;
import com.hemodialyse.backend.domain.stock.port.BonReceptionUseCase;
import com.hemodialyse.backend.infrastructure.web.dto.request.CenterActionRequest;
import com.hemodialyse.backend.infrastructure.web.dto.request.CreateBonReceptionRequest;
import com.hemodialyse.backend.infrastructure.web.dto.request.FromBonCommandeRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/stock/bons-reception")
public class BonReceptionRestController {

    private final BonReceptionUseCase useCase;

    public BonReceptionRestController(BonReceptionUseCase useCase) {
        this.useCase = useCase;
    }

    @PreAuthorize("hasAnyRole('ADMIN','PHARMACIEN')")
    @PostMapping
    public ResponseEntity<?> create(@RequestBody @Valid CreateBonReceptionRequest req) {
        List<LigneReception> lignes = req.lignes() == null ? List.of() : req.lignes().stream()
                .map(l -> new LigneReception(l.id() != null ? l.id() : UUID.randomUUID(),
                        l.articleId(), l.quantite(), l.prixUnitaire(),
                        l.numeroLot(), l.datePeremption(), l.emplacementId(), null))
                .toList();
        return ResponseEntity.ok(useCase.create(CenterId.of(req.centerId()), req.bonCommandeId(),
                req.fournisseurId(), req.dateReception(), lignes, req.userId()));
    }

    @PreAuthorize("hasAnyRole('ADMIN','PHARMACIEN')")
    @PostMapping("/from-bon-commande")
    public ResponseEntity<?> fromBonCommande(@RequestBody @Valid FromBonCommandeRequest req) {
        return ResponseEntity.ok(useCase.fromBonCommande(CenterId.of(req.centerId()), req.bonCommandeId(), req.userId()));
    }

    @PreAuthorize("hasAnyRole('ADMIN','PHARMACIEN')")
    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable UUID id, @RequestBody @Valid CreateBonReceptionRequest req) {
        List<LigneReception> lignes = req.lignes() == null ? List.of() : req.lignes().stream()
                .map(l -> new LigneReception(l.id() != null ? l.id() : UUID.randomUUID(),
                        l.articleId(), l.quantite(), l.prixUnitaire(),
                        l.numeroLot(), l.datePeremption(), l.emplacementId(), null))
                .toList();
        return ResponseEntity.ok(useCase.update(CenterId.of(req.centerId()), id,
                req.fournisseurId(), req.dateReception(), lignes));
    }

    @PreAuthorize("hasAnyRole('ADMIN','PHARMACIEN')")
    @PostMapping("/{id}/valider")
    public ResponseEntity<?> valider(@PathVariable UUID id, @RequestBody @Valid CenterActionRequest req) {
        return ResponseEntity.ok(useCase.valider(CenterId.of(req.centerId()), id, req.userId()));
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
}


