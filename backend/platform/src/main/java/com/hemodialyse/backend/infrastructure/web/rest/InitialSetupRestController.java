package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.application.setup.InitialSetupService;
import com.hemodialyse.backend.application.setup.InitialSetupService.Status;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Installation initiale (publique, à usage unique) : état de l'installation et création du compte propriétaire.
 * La création est refusée dès qu'un propriétaire existe ; voir {@link InitialSetupService}.
 */
@RestController
@RequestMapping("/api/v1/auth/setup")
public class InitialSetupRestController {

    private final InitialSetupService service;

    public InitialSetupRestController(InitialSetupService service) {
        this.service = service;
    }

    @GetMapping("/status")
    public ResponseEntity<Status> status() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.status());
    }

    @PostMapping("/superadmin")
    public ResponseEntity<Void> createOwner(@RequestBody @Valid CreateOwnerRequest request) {
        service.createOwner(request.username(), request.fullName(), request.email(), request.password(),
                request.setupToken());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    public record CreateOwnerRequest(@NotBlank @Size(max = 50) String username,
                                     @Size(max = 150) String fullName,
                                     @Size(max = 150) String email,
                                     @NotBlank @Size(max = 100) String password,
                                     @Size(max = 200) String setupToken) {
    }
}
