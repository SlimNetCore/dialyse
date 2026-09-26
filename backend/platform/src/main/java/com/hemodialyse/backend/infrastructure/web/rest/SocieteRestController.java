package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.domain.organisation.port.SocieteUseCase;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.domain.organisation.model.Societe;
import com.hemodialyse.backend.infrastructure.reporting.SocieteLogoService;
import com.hemodialyse.backend.infrastructure.security.UserPrincipal;
import com.hemodialyse.backend.infrastructure.web.dto.request.SocieteRequests.CentreRequest;
import com.hemodialyse.backend.infrastructure.web.dto.request.SocieteRequests.CreateSocieteRequest;
import com.hemodialyse.backend.infrastructure.web.dto.request.SocieteRequests.SocieteRequest;
import com.hemodialyse.backend.infrastructure.web.dto.request.SocieteRequests.TransfertCentreRequest;
import com.hemodialyse.backend.infrastructure.web.dto.response.SocieteResponse;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Gestion des sociétés et de leurs centres — réservée au SUPERADMIN (l'éditeur de la solution).
 * <p>
 * Ces routes sont transverses aux centres : elles ne sont pas soumises au {@code CenterAccessGuard}, mais
 * uniquement au rôle. Toute règle métier (au moins un centre actif par société, unicité des codes…) est portée
 * par le domaine et remonte en 422.
 */
@RestController
@RequestMapping("/api/v1/societes")
@PreAuthorize("hasRole('SUPERADMIN')")
public class SocieteRestController {

    private final SocieteUseCase useCase;
    private final SocieteLogoService logoService;

    public SocieteRestController(SocieteUseCase useCase, SocieteLogoService logoService) {
        this.useCase = useCase;
        this.logoService = logoService;
    }

    private static String currentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal) {
            return principal.getUsername();
        }
        throw new AccessDeniedException("Utilisateur non identifié");
    }

    private SocieteResponse toResponse(Societe societe) {
        return SocieteResponse.from(societe).withLogo(logoService.find(societe.id()).isPresent());
    }

    @GetMapping
    public ResponseEntity<PagedResult<SocieteResponse>> list(@RequestParam(required = false) String q,
                                                             @RequestParam(defaultValue = "0") int page,
                                                             @RequestParam(defaultValue = "20") int size) {
        PagedResult<Societe> result = useCase.lister(q, page, size);
        Set<UUID> withLogo = logoService.idsWithLogo(result.items().stream().map(Societe::id).toList());
        return ResponseEntity.ok(PagedResult.of(
                result.items().stream().map(x -> SocieteResponse.from(x).withLogo(withLogo.contains(x.id()))).toList(),
                result.total(), result.page(), result.size()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SocieteResponse> get(@PathVariable UUID id) {
        return useCase.trouver(id).map(this::toResponse).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<SocieteResponse> create(@RequestBody @Valid CreateSocieteRequest request) {
        var societe = useCase.creer(request.societe().toData(), request.premierCentre().toData());
        return ResponseEntity.status(HttpStatus.CREATED).body(SocieteResponse.from(societe));
    }

    @PutMapping("/{id}")
    public ResponseEntity<SocieteResponse> update(@PathVariable UUID id, @RequestBody @Valid SocieteRequest request) {
        return ResponseEntity.ok(toResponse(useCase.modifier(id, request.toData())));
    }

    @PostMapping("/{id}/activer")
    public ResponseEntity<SocieteResponse> activate(@PathVariable UUID id) {
        return ResponseEntity.ok(toResponse(useCase.activer(id)));
    }

    // ───────────────────────────── Logo ─────────────────────────────

    @PostMapping("/{id}/desactiver")
    public ResponseEntity<SocieteResponse> deactivate(@PathVariable UUID id) {
        return ResponseEntity.ok(toResponse(useCase.desactiver(id)));
    }

    /**
     * Envoie le logo (PNG ou JPEG) : le contenu est contrôlé côté serveur (signature, taille, dimensions).
     */
    @PutMapping(value = "/{id}/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadLogo(@PathVariable UUID id, @RequestParam("file") MultipartFile file)
            throws IOException {
        var outcome = logoService.save(id, file.getBytes(), currentUsername());
        if (outcome.isEmpty()) return ResponseEntity.notFound().build();
        if (!outcome.get().accepted()) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(Map.of("violations",
                    List.of(Map.of("code", outcome.get().errorCode(), "detail", outcome.get().detail()))));
        }
        return useCase.trouver(id).<ResponseEntity<?>>map(x -> ResponseEntity.ok(toResponse(x)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}/logo")
    public ResponseEntity<Void> deleteLogo(@PathVariable UUID id) {
        return logoService.delete(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    @GetMapping("/{id}/logo")
    public ResponseEntity<byte[]> logo(@PathVariable UUID id) {
        return logoService.find(id).map(logo -> {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(logo.contentType()));
            headers.setCacheControl(CacheControl.noStore());
            headers.add("X-Content-Type-Options", "nosniff");
            headers.add("Content-Security-Policy", "default-src 'none'");
            return ResponseEntity.ok().headers(headers).body(logo.content());
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    // ───────────────────────────── Centres ─────────────────────────────

    @PostMapping("/{id}/centres")
    public ResponseEntity<SocieteResponse> addCentre(@PathVariable UUID id, @RequestBody @Valid CentreRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(toResponse(useCase.ajouterCentre(id, request.toData())));
    }

    @PutMapping("/{id}/centres/{centreId}")
    public ResponseEntity<SocieteResponse> updateCentre(@PathVariable UUID id, @PathVariable UUID centreId,
                                                        @RequestBody @Valid CentreRequest request) {
        return ResponseEntity.ok(toResponse(useCase.modifierCentre(id, centreId, request.toData())));
    }

    @PostMapping("/{id}/centres/{centreId}/activer")
    public ResponseEntity<SocieteResponse> activateCentre(@PathVariable UUID id, @PathVariable UUID centreId) {
        return ResponseEntity.ok(toResponse(useCase.activerCentre(id, centreId)));
    }

    @PostMapping("/{id}/centres/{centreId}/desactiver")
    public ResponseEntity<SocieteResponse> deactivateCentre(@PathVariable UUID id, @PathVariable UUID centreId) {
        return ResponseEntity.ok(toResponse(useCase.desactiverCentre(id, centreId)));
    }

    @PostMapping("/{id}/centres/{centreId}/transfert")
    public ResponseEntity<Void> transferCentre(@PathVariable UUID id, @PathVariable UUID centreId,
                                               @RequestBody @Valid TransfertCentreRequest request) {
        useCase.transfererCentre(id, centreId, request.societeCibleId());
        return ResponseEntity.noContent().build();
    }
}
