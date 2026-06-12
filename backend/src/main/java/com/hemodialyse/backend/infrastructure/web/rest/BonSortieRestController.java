package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.domain.stock.model.SortieRequestItem;
import com.hemodialyse.backend.domain.stock.port.BonSortieUseCase;
import com.hemodialyse.backend.infrastructure.web.dto.request.CreateBonSortieRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/stock/bons-sortie")
public class BonSortieRestController {

    private final BonSortieUseCase useCase;

    public BonSortieRestController(BonSortieUseCase useCase) {
        this.useCase = useCase;
    }

    @PreAuthorize("hasAnyRole('ADMIN','PHARMACIEN','INFIRMIER')")
    @PostMapping
    public ResponseEntity<?> create(@RequestBody @Valid CreateBonSortieRequest req) {
        List<SortieRequestItem> items = req.items() == null ? List.of() : req.items().stream()
                .map(i -> new SortieRequestItem(i.articleId(), i.quantite()))
                .toList();
        return ResponseEntity.ok(useCase.create(CenterId.of(req.centerId()), req.seanceId(), req.patientId(),
                req.poste(), req.dateSortie(), items, req.userId()));
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

