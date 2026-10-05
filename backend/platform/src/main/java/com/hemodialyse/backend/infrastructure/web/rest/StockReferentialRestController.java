package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.domain.article.model.Article;
import com.hemodialyse.backend.domain.article.model.TypeTraitementAnemie;
import com.hemodialyse.backend.infrastructure.security.CenterAccessGuard;
import com.hemodialyse.backend.infrastructure.web.dto.response.PagedResponse;
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
    private final CenterAccessGuard centerAccessGuard;

    public StockReferentialRestController(StockReferentialUseCase useCase, CenterAccessGuard centerAccessGuard) {
        this.useCase = useCase;
        this.centerAccessGuard = centerAccessGuard;
    }

    @PreAuthorize("hasAnyRole('ADMIN','PHARMACIEN')")
    @PostMapping("/articles")
    public ResponseEntity<?> createArticle(@RequestBody @Valid CreateArticleRequest req) {
        var center = centerAccessGuard.requireCenter(req.centerId());
        return ResponseEntity.ok(useCase.createArticle(center, req.toFiche()));
    }

    @PreAuthorize("hasAnyRole('ADMIN','PHARMACIEN')")
    @PutMapping("/articles/{articleId}")
    public ResponseEntity<?> updateArticle(@PathVariable UUID articleId,
                                           @RequestBody @Valid CreateArticleRequest req) {
        var center = centerAccessGuard.requireCenter(req.centerId());
        return ResponseEntity.ok(useCase.updateArticle(center, articleId, req.toFiche()));
    }

    @PreAuthorize("hasAnyRole('ADMIN','PHARMACIEN','INFIRMIER','MEDECIN')")
    @GetMapping("/articles/{articleId}")
    public ResponseEntity<?> getArticle(@PathVariable UUID articleId, @RequestParam(required = false) UUID centerId) {
        return ResponseEntity.ok(useCase.getArticle(centerAccessGuard.requireCenter(centerId), articleId));
    }

    @PreAuthorize("hasAnyRole('ADMIN','PHARMACIEN')")
    @PatchMapping("/articles/{articleId}/active")
    public ResponseEntity<?> setArticleActive(@PathVariable UUID articleId,
                                              @RequestParam(required = false) UUID centerId,
                                              @RequestParam boolean active) {
        return ResponseEntity.ok(useCase.setArticleActive(centerAccessGuard.requireCenter(centerId), articleId, active));
    }

    /**
     * Fiches articles paginées (écran « Articles ») : recherche sur code, libellé, DCI ou code-barres.
     */
    @PreAuthorize("hasAnyRole('ADMIN','PHARMACIEN')")
    @GetMapping("/articles/page")
    public ResponseEntity<PagedResponse<Article>> searchArticles(@RequestParam(required = false) UUID centerId,
                                                                 @RequestParam(required = false) String q,
                                                                 @RequestParam(required = false) Boolean active,
                                                                 @RequestParam(defaultValue = "0") int page,
                                                                 @RequestParam(defaultValue = "20") int size) {
        var paged = useCase.searchArticles(centerAccessGuard.requireCenter(centerId), q, active, page, size);
        return ResponseEntity.ok(PagedResponse.from(paged, a -> a));
    }

    /**
     * Liste des articles du centre — {@code typeTraitementAnemie} filtre sur EPO/FER_INJECTABLE
     * pour alimenter les listes déroulantes de la prescription médicale (accès MEDECIN inclus,
     * seul cas d'usage nécessitant ce rôle sur le référentiel stock).
     */
    @PreAuthorize("hasAnyRole('ADMIN','PHARMACIEN','INFIRMIER','MEDECIN')")
    @GetMapping("/articles")
    public ResponseEntity<?> listArticles(@RequestParam UUID centerId,
                                          @RequestParam(required = false) String typeTraitementAnemie) {
        if (typeTraitementAnemie != null && !typeTraitementAnemie.isBlank()) {
            return ResponseEntity.ok(useCase.listArticlesByTypeTraitementAnemie(
                    CenterId.of(centerId), TypeTraitementAnemie.valueOf(typeTraitementAnemie)));
        }
        return ResponseEntity.ok(useCase.listArticles(CenterId.of(centerId)));
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

