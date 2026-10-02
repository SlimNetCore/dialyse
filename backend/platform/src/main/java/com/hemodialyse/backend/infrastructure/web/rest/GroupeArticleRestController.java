package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.application.stock.GroupeArticleApplicationService;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.domain.stock.model.GroupeArticle;
import com.hemodialyse.backend.infrastructure.security.CenterAccessGuard;
import com.hemodialyse.backend.infrastructure.web.dto.stock.GroupeArticleDtos.GroupeArticleRequest;
import com.hemodialyse.backend.infrastructure.web.dto.stock.GroupeArticleDtos.GroupeArticleResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Administration des groupes d'articles du centre (ex. « KIT CNAS »), dont la valorisation est suivie sur le
 * tableau de bord de la direction. Isolation multi-centre via {@link CenterAccessGuard} ; liste paginée.
 */
@RestController
@RequestMapping("/api/v1/stock/groupes-articles")
@PreAuthorize("hasRole('ADMIN')")
public class GroupeArticleRestController {

    private final GroupeArticleApplicationService service;
    private final CenterAccessGuard centerAccessGuard;

    public GroupeArticleRestController(GroupeArticleApplicationService service, CenterAccessGuard centerAccessGuard) {
        this.service = service;
        this.centerAccessGuard = centerAccessGuard;
    }

    @GetMapping
    public PagedResult<GroupeArticleResponse> lister(
            @RequestParam(required = false) UUID centerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PagedResult<GroupeArticle> paged = service.lister(centre(centerId), page, size);
        return new PagedResult<>(paged.items().stream().map(GroupeArticleResponse::of).toList(),
                paged.total(), paged.page(), paged.size());
    }

    @GetMapping("/{id}")
    public GroupeArticleResponse obtenir(@PathVariable UUID id, @RequestParam(required = false) UUID centerId) {
        return GroupeArticleResponse.of(service.obtenir(centre(centerId), id));
    }

    @PostMapping
    public ResponseEntity<GroupeArticleResponse> creer(
            @RequestParam(required = false) UUID centerId, @Valid @RequestBody GroupeArticleRequest request) {
        GroupeArticle cree = service.creer(centre(centerId), request.nom(), request.description(), request.articleIds());
        return ResponseEntity.status(HttpStatus.CREATED).body(GroupeArticleResponse.of(cree));
    }

    @PutMapping("/{id}")
    public GroupeArticleResponse modifier(
            @PathVariable UUID id, @RequestParam(required = false) UUID centerId,
            @Valid @RequestBody GroupeArticleRequest request) {
        return GroupeArticleResponse.of(
                service.modifier(centre(centerId), id, request.nom(), request.description(), request.articleIds()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> supprimer(@PathVariable UUID id, @RequestParam(required = false) UUID centerId) {
        service.supprimer(centre(centerId), id);
        return ResponseEntity.noContent().build();
    }

    private UUID centre(UUID requested) {
        return centerAccessGuard.requireCenter(requested).value();
    }
}
