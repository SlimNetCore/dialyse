package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.domain.stock.port.StockReferentialUseCase;
import com.hemodialyse.backend.infrastructure.web.dto.request.CreateArticleRequest;
import com.hemodialyse.backend.infrastructure.web.dto.request.CreateEmplacementRequest;
import com.hemodialyse.backend.infrastructure.web.dto.request.CreateFournisseurRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/stock/referentiel")
public class StockReferentialRestController {

    private final StockReferentialUseCase useCase;

    public StockReferentialRestController(StockReferentialUseCase useCase) {
        this.useCase = useCase;
    }

    @PreAuthorize("hasAnyRole('ADMIN','PHARMACIEN')")
    @PostMapping("/articles")
    public ResponseEntity<?> createArticle(@RequestBody @Valid CreateArticleRequest req) {
        return ResponseEntity.ok(useCase.createArticle(
                CenterId.of(req.centerId()),
                req.code(),
                req.libelle(),
                req.unite(),
                req.seuilAlerte(),
                req.gereParLot()
        ));
    }

    @PreAuthorize("hasAnyRole('ADMIN','PHARMACIEN')")
    @PostMapping("/fournisseurs")
    public ResponseEntity<?> createFournisseur(@RequestBody @Valid CreateFournisseurRequest req) {
        return ResponseEntity.ok(useCase.createFournisseur(CenterId.of(req.centerId()), req.code(),
                req.raisonSociale(), req.contact(), req.telephone(), req.email()));
    }

    @PreAuthorize("hasAnyRole('ADMIN','PHARMACIEN','INFIRMIER')")
    @GetMapping("/fournisseurs")
    public ResponseEntity<?> listFournisseurs(@RequestParam UUID centerId,
                                              @RequestParam(required = false) String q) {
        return ResponseEntity.ok(useCase.searchFournisseurs(CenterId.of(centerId), q));
    }

    @PreAuthorize("hasAnyRole('ADMIN','PHARMACIEN')")
    @PostMapping("/emplacements")
    public ResponseEntity<?> createEmplacement(@RequestBody @Valid CreateEmplacementRequest req) {
        return ResponseEntity.ok(useCase.createEmplacement(CenterId.of(req.centerId()), req.code(), req.libelle()));
    }

    @PreAuthorize("hasAnyRole('ADMIN','PHARMACIEN','INFIRMIER')")
    @GetMapping("/emplacements")
    public ResponseEntity<?> listEmplacements(@RequestParam UUID centerId) {
        return ResponseEntity.ok(useCase.listEmplacements(CenterId.of(centerId)));
    }
}

