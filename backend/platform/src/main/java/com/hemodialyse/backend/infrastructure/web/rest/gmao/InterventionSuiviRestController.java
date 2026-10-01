package com.hemodialyse.backend.infrastructure.web.rest.gmao;

import com.hemodialyse.backend.application.gmao.InterventionSuiviQueryService;
import com.hemodialyse.backend.infrastructure.reporting.BonInterventionReportService;
import com.hemodialyse.backend.infrastructure.reporting.ModeleDocumentPrinter;
import com.hemodialyse.backend.infrastructure.security.UserPrincipal;
import com.hemodialyse.backend.infrastructure.web.dto.response.gmao.EvenementInterventionResponse;
import com.hemodialyse.backend.infrastructure.web.dto.response.gmao.IndicateursInterventionResponse;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Suivi d'une intervention : indicateurs de réactivité et ligne de temps. Lecture seule, bornée au centre.
 */
@RestController
@RequestMapping("/api/v1/gmao/interventions/{id}")
@PreAuthorize("hasRole('ADMIN')")
public class InterventionSuiviRestController {

    private final InterventionSuiviQueryService suivi;
    private final BonInterventionReportService bon;

    public InterventionSuiviRestController(InterventionSuiviQueryService suivi, BonInterventionReportService bon) {
        this.suivi = suivi;
        this.bon = bon;
    }

    static ZoneId zone(String tz) {
        if (tz == null || tz.isBlank()) return ZoneOffset.UTC;
        try {
            return ZoneId.of(tz.trim());
        } catch (DateTimeException e) {
            return ZoneOffset.UTC;
        }
    }

    /**
     * Bon d'intervention imprimable (modèle de document du centre). {@code tz} = fuseau IANA du navigateur
     * (ex. Africa/Algiers) pour l'affichage des dates stockées en UTC ; UTC à défaut ou si invalide.
     */
    @GetMapping("/bon")
    public ResponseEntity<byte[]> bon(
            @PathVariable UUID id, @RequestParam(required = false) String tz, Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        ModeleDocumentPrinter.Document doc = bon.imprimer(id, centreId(authentication), zone(tz), principal.getUsername());
        String base = "bon-intervention-" + id.toString().substring(0, 8);
        return switch (doc.format().toUpperCase(Locale.ROOT)) {
            case "EXCEL", "XLS", "XLSX" -> ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                            .filename(base + ".xlsx", StandardCharsets.UTF_8).build().toString())
                    .body(doc.content());
            case "HTML" -> ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(doc.content());
            default -> ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                            .filename(base + ".pdf", StandardCharsets.UTF_8).build().toString())
                    .body(doc.content());
        };
    }

    @GetMapping("/indicateurs")
    public ResponseEntity<IndicateursInterventionResponse> indicateurs(
            @PathVariable UUID id, Authentication authentication) {
        return ResponseEntity.ok(new IndicateursInterventionResponse(suivi.indicateurs(id, centreId(authentication))));
    }

    @GetMapping("/chronologie")
    public ResponseEntity<List<EvenementInterventionResponse>> chronologie(
            @PathVariable UUID id, Authentication authentication) {
        return ResponseEntity.ok(suivi.chronologie(id, centreId(authentication)).stream()
                .map(e -> new EvenementInterventionResponse(
                        e.evenement().type(), e.evenement().at(), e.evenement().par(), e.parNom()))
                .toList());
    }

    private UUID centreId(Authentication authentication) {
        return UUID.fromString(((UserPrincipal) authentication.getPrincipal()).getCenterId());
    }
}
