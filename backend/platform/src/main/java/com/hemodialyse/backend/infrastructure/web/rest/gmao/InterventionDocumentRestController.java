package com.hemodialyse.backend.infrastructure.web.rest.gmao;

import com.hemodialyse.backend.domain.gmao.model.DocumentIntervention;
import com.hemodialyse.backend.domain.gmao.model.Intervention;
import com.hemodialyse.backend.domain.gmao.model.TypeDocumentIntervention;
import com.hemodialyse.backend.domain.gmao.port.DocumentInterventionRepositoryPort;
import com.hemodialyse.backend.domain.gmao.port.InterventionRepositoryPort;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.infrastructure.security.UserPrincipal;
import com.hemodialyse.backend.infrastructure.web.dto.response.gmao.DocumentInterventionResponse;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Pièces justificatives d'une intervention (bon d'intervention, facture, photo) : envoi, liste paginée,
 * téléchargement et suppression. Toujours bornées au centre de l'appelant (AGENTS.md §2) ; le contenu est
 * validé sur sa signature (PDF/PNG/JPEG, 5 Mo max) et servi en pièce jointe ({@code nosniff}).
 */
@RestController
@RequestMapping("/api/v1/gmao/interventions/{interventionId}/documents")
@PreAuthorize("hasRole('ADMIN')")
public class InterventionDocumentRestController {

    private final InterventionRepositoryPort interventionRepository;
    private final DocumentInterventionRepositoryPort documentRepository;

    public InterventionDocumentRestController(
            InterventionRepositoryPort interventionRepository, DocumentInterventionRepositoryPort documentRepository) {
        this.interventionRepository = interventionRepository;
        this.documentRepository = documentRepository;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentInterventionResponse> ajouter(
            @PathVariable UUID interventionId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "AUTRE") String type,
            Authentication authentication) throws IOException {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        Intervention intervention = requireIntervention(interventionId, centreId(authentication));
        if (documentRepository.countByInterventionId(interventionId) >= DocumentIntervention.MAX_PAR_INTERVENTION) {
            throw new IllegalArgumentException("Nombre maximal de documents atteint pour cette intervention");
        }

        DocumentIntervention document = DocumentIntervention.creer(
                intervention.getId(), intervention.getCentreId(), TypeDocumentIntervention.valueOf(type),
                file.getOriginalFilename(), file.getBytes(), UUID.fromString(principal.getId()));
        documentRepository.save(document);

        return ResponseEntity.status(HttpStatus.CREATED).body(new DocumentInterventionResponse(document));
    }

    @GetMapping
    public ResponseEntity<PagedResult<DocumentInterventionResponse>> lister(
            @PathVariable UUID interventionId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        requireIntervention(interventionId, centreId(authentication));
        PagedResult<DocumentIntervention> paged = documentRepository.findPagedByInterventionId(interventionId, page, size);
        return ResponseEntity.ok(new PagedResult<>(
                paged.items().stream().map(DocumentInterventionResponse::new).toList(),
                paged.total(), paged.page(), paged.size()));
    }

    @GetMapping("/{documentId}")
    public ResponseEntity<byte[]> telecharger(
            @PathVariable UUID interventionId, @PathVariable UUID documentId, Authentication authentication) {
        requireIntervention(interventionId, centreId(authentication));
        DocumentIntervention document = requireDocument(interventionId, documentId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(document.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(document.nom(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(document.contenu());
    }

    @DeleteMapping("/{documentId}")
    public ResponseEntity<Void> supprimer(
            @PathVariable UUID interventionId, @PathVariable UUID documentId, Authentication authentication) {
        requireIntervention(interventionId, centreId(authentication));
        requireDocument(interventionId, documentId);
        documentRepository.delete(documentId);
        return ResponseEntity.noContent().build();
    }

    private Intervention requireIntervention(UUID id, UUID centreId) {
        return interventionRepository.findById(id)
                .filter(i -> i.getCentreId().equals(centreId))
                .orElseThrow(() -> new IllegalArgumentException("Intervention non trouvée"));
    }

    private DocumentIntervention requireDocument(UUID interventionId, UUID documentId) {
        return documentRepository.findWithContenuById(documentId)
                .filter(d -> d.interventionId().equals(interventionId))
                .orElseThrow(() -> new IllegalArgumentException("Document non trouvé"));
    }

    private UUID centreId(Authentication authentication) {
        return UUID.fromString(((UserPrincipal) authentication.getPrincipal()).getCenterId());
    }
}
