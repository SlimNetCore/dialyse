package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.application.direction.SocieteAdminAccountService;
import com.hemodialyse.backend.application.direction.SocieteAdminAccountService.Account;
import com.hemodialyse.backend.infrastructure.security.UserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Administrateurs des centres d'une société — créés et gérés par le propriétaire de l'application (SUPERADMIN)
 * uniquement. Le mot de passe n'est jamais renvoyé ni journalisé.
 */
@RestController
@RequestMapping("/api/v1/societes/{societeId}/admin-accounts")
@PreAuthorize("hasRole('SUPERADMIN')")
public class SocieteAdminAccountRestController {

    private final SocieteAdminAccountService service;

    public SocieteAdminAccountRestController(SocieteAdminAccountService service) {
        this.service = service;
    }

    private static String currentUsername(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal) {
            return principal.getUsername();
        }
        throw new AccessDeniedException("Utilisateur non identifié");
    }

    @GetMapping
    public ResponseEntity<List<Account>> list(@PathVariable UUID societeId) {
        return ResponseEntity.ok(service.list(societeId));
    }

    @PostMapping
    public ResponseEntity<Account> create(@PathVariable UUID societeId, @RequestBody @Valid CreateAdminRequest request,
                                          Authentication authentication) {
        Account created = service.create(societeId, request.centerId(), request.username(), request.fullName(),
                request.email(), request.password(), currentUsername(authentication));
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/{userId}/activer")
    public ResponseEntity<Void> activate(@PathVariable UUID societeId, @PathVariable UUID userId) {
        service.setActive(societeId, userId, true);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{userId}/desactiver")
    public ResponseEntity<Void> deactivate(@PathVariable UUID societeId, @PathVariable UUID userId) {
        service.setActive(societeId, userId, false);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{userId}/password")
    public ResponseEntity<Void> resetPassword(@PathVariable UUID societeId, @PathVariable UUID userId,
                                              @RequestBody @Valid ResetPasswordRequest request) {
        service.resetPassword(societeId, userId, request.password());
        return ResponseEntity.noContent().build();
    }

    public record CreateAdminRequest(@NotNull UUID centerId,
                                     @NotBlank @Size(max = 50) String username,
                                     @Size(max = 150) String fullName,
                                     @Size(max = 150) String email,
                                     @NotBlank @Size(max = 100) String password) {
    }

    public record ResetPasswordRequest(@NotBlank @Size(max = 100) String password) {
    }
}
