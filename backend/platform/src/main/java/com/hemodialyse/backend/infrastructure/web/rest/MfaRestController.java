package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.application.auth.mfa.MfaService;
import com.hemodialyse.backend.application.auth.mfa.MfaService.Enrollment;
import com.hemodialyse.backend.infrastructure.security.UserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Double authentification TOTP de l'utilisateur connecté (tous rôles) : état, inscription, confirmation par un
 * premier code, désactivation. Le secret et les codes de secours ne sont renvoyés qu'à l'inscription.
 */
@RestController
@RequestMapping("/api/v1/auth/mfa")
public class MfaRestController {

    private final MfaService service;

    public MfaRestController(MfaService service) {
        this.service = service;
    }

    private static UserPrincipal principal(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal p) {
            return p;
        }
        throw new AccessDeniedException("Utilisateur non identifié");
    }

    private static UUID userId(Authentication authentication) {
        return UUID.fromString(principal(authentication).getId());
    }

    private static String username(Authentication authentication) {
        return principal(authentication).getUsername();
    }

    @GetMapping("/status")
    public ResponseEntity<StatusResponse> status(Authentication authentication) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new StatusResponse(service.isEnabled(userId(authentication))));
    }

    @PostMapping("/enroll")
    public ResponseEntity<Enrollment> enroll(Authentication authentication) {
        Enrollment enrollment = service.beginEnrollment(userId(authentication), username(authentication));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(enrollment);
    }

    @PostMapping("/confirm")
    public ResponseEntity<RecoveryCodesResponse> confirm(@RequestBody @Valid CodeRequest request,
                                                         Authentication authentication) {
        List<String> codes = service.confirmEnrollment(userId(authentication), request.code());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new RecoveryCodesResponse(codes));
    }

    @PostMapping("/disable")
    public ResponseEntity<Void> disable(@RequestBody @Valid CodeRequest request, Authentication authentication) {
        service.disable(userId(authentication), request.code());
        return ResponseEntity.noContent().build();
    }

    public record CodeRequest(@NotBlank @Size(max = 20) String code) {
    }

    public record StatusResponse(boolean enabled) {
    }

    public record RecoveryCodesResponse(List<String> recoveryCodes) {
    }
}
