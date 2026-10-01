package com.hemodialyse.backend.infrastructure.web.rest.gmao;

import com.hemodialyse.backend.domain.gmao.model.Intervenant;
import com.hemodialyse.backend.domain.gmao.model.TypeIntervenant;
import com.hemodialyse.backend.domain.gmao.port.IntervenantRepositoryPort;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.infrastructure.security.UserPrincipal;
import com.hemodialyse.backend.infrastructure.web.dto.request.gmao.CreateIntervenantRequest;
import com.hemodialyse.backend.infrastructure.web.dto.response.gmao.IntervenantResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Référentiel Intervenant GMAO (technicien interne / prestataire externe) — permet le suivi du coût
 * de maintenance par prestataire. Isolation multi-centre systématique (AGENTS.md §2) ; liste
 * obligatoirement paginée (AGENTS.md §9).
 */
@RestController
@RequestMapping("/api/v1/gmao/intervenants")
@PreAuthorize("hasRole('ADMIN')")
public class IntervenantRestController {

    private final IntervenantRepositoryPort intervenantRepository;

    public IntervenantRestController(IntervenantRepositoryPort intervenantRepository) {
        this.intervenantRepository = intervenantRepository;
    }

    @PostMapping
    public ResponseEntity<IntervenantResponse> creer(
            @Valid @RequestBody CreateIntervenantRequest request, Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();

        Intervenant intervenant = Intervenant.creer(
                UUID.fromString(principal.getCenterId()), request.nom(), TypeIntervenant.valueOf(request.type()),
                request.telephone(), request.email(), request.tarifHoraireDefaut());
        intervenantRepository.save(intervenant);

        return ResponseEntity.status(HttpStatus.CREATED).body(new IntervenantResponse(intervenant));
    }

    @GetMapping
    public ResponseEntity<PagedResult<IntervenantResponse>> lister(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();

        PagedResult<Intervenant> paged = intervenantRepository.findPaged(
                UUID.fromString(principal.getCenterId()), page, size);

        return ResponseEntity.ok(new PagedResult<>(
                paged.items().stream().map(IntervenantResponse::new).toList(),
                paged.total(), paged.page(), paged.size()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<IntervenantResponse> modifier(
            @PathVariable String id, @Valid @RequestBody CreateIntervenantRequest request,
            Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        UUID centreId = UUID.fromString(principal.getCenterId());

        Intervenant existant = intervenantRepository.findById(UUID.fromString(id))
                .filter(i -> i.centreId().equals(centreId))
                .orElseThrow(() -> new IllegalArgumentException("Intervenant non trouvé"));

        Intervenant modifie = existant.modifier(
                request.nom(), TypeIntervenant.valueOf(request.type()), request.telephone(),
                request.email(), request.tarifHoraireDefaut());
        intervenantRepository.save(modifie);

        return ResponseEntity.ok(new IntervenantResponse(modifie));
    }

    @PostMapping("/{id}/desactiver")
    public ResponseEntity<IntervenantResponse> desactiver(@PathVariable String id, Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        UUID centreId = UUID.fromString(principal.getCenterId());

        Intervenant existant = intervenantRepository.findById(UUID.fromString(id))
                .filter(i -> i.centreId().equals(centreId))
                .orElseThrow(() -> new IllegalArgumentException("Intervenant non trouvé"));

        Intervenant desactive = existant.desactiver();
        intervenantRepository.save(desactive);

        return ResponseEntity.ok(new IntervenantResponse(desactive));
    }
}
