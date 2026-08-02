package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.domain.article.port.ArticleRepositoryPort;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.domain.stock.model.SortieRequestItem;
import com.hemodialyse.backend.domain.stock.port.BonSortieUseCase;
import com.hemodialyse.backend.domain.stock.port.LotRepositoryPort;
import com.hemodialyse.backend.infrastructure.web.dto.request.CreateBonSortieRequest;
import com.hemodialyse.backend.infrastructure.web.dto.request.UpdateBonSortieRequest;
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
    private final LotRepositoryPort lotRepo;
    private final ArticleRepositoryPort articleRepo;

    public BonSortieRestController(BonSortieUseCase useCase, LotRepositoryPort lotRepo, ArticleRepositoryPort articleRepo) {
        this.useCase = useCase;
        this.lotRepo = lotRepo;
        this.articleRepo = articleRepo;
    }

    @PreAuthorize("hasAnyRole('ADMIN','PHARMACIEN','INFIRMIER')")
    @PostMapping
    public ResponseEntity<?> create(@RequestBody @Valid CreateBonSortieRequest req) {
        List<SortieRequestItem> items = req.items() == null ? List.of() : req.items().stream()
                .map(i -> new SortieRequestItem(i.articleId(), i.lotId(), i.quantite()))
                .toList();
        return ResponseEntity.ok(useCase.create(CenterId.of(req.centerId()), req.seanceId(), req.patientId(),
                req.poste(), req.dateSortie(), items, req.userId()));
    }

    @PreAuthorize("hasAnyRole('ADMIN','PHARMACIEN','INFIRMIER')")
    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable UUID id, @RequestBody @Valid UpdateBonSortieRequest req) {
        List<SortieRequestItem> items = req.items() == null ? List.of() : req.items().stream()
                .map(i -> new SortieRequestItem(i.articleId(), i.lotId(), i.quantite()))
                .toList();
        return ResponseEntity.ok(useCase.update(CenterId.of(req.centerId()), id, req.seanceId(), req.patientId(),
                req.poste(), req.dateSortie(), items, req.userId()));
    }

    @PreAuthorize("hasAnyRole('ADMIN','PHARMACIEN','INFIRMIER')")
    @GetMapping("/lots-disponibles")
    public ResponseEntity<?> lotsDisponibles(@RequestParam UUID centerId, @RequestParam UUID articleId) {
        var center = CenterId.of(centerId);
        var pmpCourant = articleRepo.findById(articleId, center)
                .map(a -> a.getPmpCourant())
                .orElse(java.math.BigDecimal.ZERO);
        var data = lotRepo.findAvailableByArticleFefo(articleId, CenterId.of(centerId)).stream()
                .map(l -> new LotDisponibleDto(
                        l.getId(),
                        l.getNumeroLot(),
                        l.getDatePeremption(),
                        pmpCourant,
                        l.getQuantiteRestante()
                ))
                .toList();
        return ResponseEntity.ok(data);
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

    private record LotDisponibleDto(
            UUID id,
            String numeroLot,
            java.time.LocalDate datePeremption,
            java.math.BigDecimal pmp,
            java.math.BigDecimal quantiteRestante
    ) {
    }
}

