package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.application.reporting.JrxmlSecurityValidator.Violation;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.infrastructure.reporting.ModeleDocumentTemplateService;
import com.hemodialyse.backend.infrastructure.reporting.ModeleDocumentTemplateService.ModeleInfo;
import com.hemodialyse.backend.infrastructure.reporting.ModeleDocumentTemplateService.VersionView;
import com.hemodialyse.backend.infrastructure.security.CenterAccessGuard;
import com.hemodialyse.backend.infrastructure.security.UserPrincipal;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Personnalisation d'un modèle d'impression : téléchargement du modèle courant, téléversement d'une version
 * modifiée (validée strictement côté serveur), historique, retour arrière et retour au modèle d'origine.
 * <p>
 * Réservé à l'ADMIN du centre : le centre demandé est confronté à celui du jeton, et le modèle doit appartenir
 * à ce centre.
 */
@RestController
@RequestMapping("/api/v1/documents/modeles/{modeleId}")
@PreAuthorize("hasAnyRole('ADMIN','SUPERADMIN')")
public class ModeleDocumentTemplateRestController {

    private final ModeleDocumentTemplateService service;
    private final CenterAccessGuard centerAccessGuard;
    public ModeleDocumentTemplateRestController(ModeleDocumentTemplateService service,
                                                CenterAccessGuard centerAccessGuard) {
        this.service = service;
        this.centerAccessGuard = centerAccessGuard;
    }

    private static String currentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal) {
            return principal.getUsername();
        }
        throw new AccessDeniedException("Utilisateur non identifié");
    }

    private static String safeFilename(String code) {
        String base = code == null ? "modele" : code.replaceAll("[^A-Za-z0-9_\\-]", "_");
        return base.isBlank() ? "modele" : base.toLowerCase();
    }

    @GetMapping("/versions")
    public ResponseEntity<VersionsResponse> versions(@PathVariable UUID modeleId,
                                                     @RequestParam(required = false) UUID centerId,
                                                     @RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "20") int size) {
        UUID center = centerAccessGuard.requireCenter(centerId).value();
        if (service.findModele(center, modeleId).isEmpty()) return ResponseEntity.notFound().build();
        PagedResult<VersionView> result = service.listVersions(center, modeleId, page, size);
        Integer active = service.activeCustom(center, modeleId)
                .map(ModeleDocumentTemplateService.ActiveCustomTemplate::version).orElse(null);
        return ResponseEntity.ok(new VersionsResponse(result.items(), result.total(), result.page(), result.size(), active));
    }

    /**
     * Télécharge le modèle à éditer : la version {@code version} si fournie, sinon le modèle en vigueur
     * (version personnalisée active, à défaut le modèle d'origine). {@code origine=true} force le modèle d'origine.
     */
    @GetMapping("/source")
    public ResponseEntity<byte[]> source(@PathVariable UUID modeleId,
                                         @RequestParam(required = false) UUID centerId,
                                         @RequestParam(required = false) Integer version,
                                         @RequestParam(defaultValue = "false") boolean origine) {
        UUID center = centerAccessGuard.requireCenter(centerId).value();
        Optional<ModeleInfo> modele = service.findModele(center, modeleId);
        if (modele.isEmpty()) return ResponseEntity.notFound().build();

        Optional<String> content;
        if (version != null) {
            content = service.versionSource(center, modeleId, version);
        } else if (origine) {
            content = service.builtInSource(modele.get());
        } else {
            content = service.activeCustom(center, modeleId)
                    .map(ModeleDocumentTemplateService.ActiveCustomTemplate::contenu)
                    .or(() -> service.builtInSource(modele.get()));
        }
        if (content.isEmpty()) return ResponseEntity.notFound().build();

        String filename = safeFilename(modele.get().code()) + (version != null ? "-v" + version : "") + ".jrxml";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_XML);
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build());
        headers.add("X-Content-Type-Options", "nosniff");
        return ResponseEntity.ok().headers(headers).body(content.get().getBytes(StandardCharsets.UTF_8));
    }

    @PostMapping(value = "/versions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> upload(@PathVariable UUID modeleId,
                                    @RequestParam(required = false) UUID centerId,
                                    @RequestParam("file") MultipartFile file,
                                    @RequestParam(required = false) String commentaire) throws IOException {
        UUID center = centerAccessGuard.requireCenter(centerId).value();
        Optional<ModeleInfo> modele = service.findModele(center, modeleId);
        if (modele.isEmpty()) return ResponseEntity.notFound().build();

        var outcome = service.upload(modele.get(), file.getOriginalFilename(), file.getBytes(), commentaire, currentUsername());
        if (outcome.accepted()) {
            return ResponseEntity.status(HttpStatus.CREATED).body(outcome.version());
        }
        List<Violation> violations = outcome.violations();
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(Map.of("violations", violations));
    }

    @PostMapping("/versions/{version}/activate")
    public ResponseEntity<VersionView> activate(@PathVariable UUID modeleId, @PathVariable int version,
                                                @RequestParam(required = false) UUID centerId) {
        UUID center = centerAccessGuard.requireCenter(centerId).value();
        return service.activate(center, modeleId, version)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/reset")
    public ResponseEntity<Void> reset(@PathVariable UUID modeleId, @RequestParam(required = false) UUID centerId) {
        UUID center = centerAccessGuard.requireCenter(centerId).value();
        if (service.findModele(center, modeleId).isEmpty()) return ResponseEntity.notFound().build();
        service.reset(center, modeleId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Page d'historique + numéro de la version personnalisée en vigueur ({@code null} = modèle d'origine).
     */
    public record VersionsResponse(List<VersionView> items, long total, int page, int size, Integer activeVersion) {
    }
}
