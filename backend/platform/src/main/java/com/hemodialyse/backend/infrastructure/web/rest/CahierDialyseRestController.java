package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.infrastructure.reporting.CahierDialyseReportService;
import com.hemodialyse.backend.infrastructure.reporting.ModeleDocumentPrinter;
import com.hemodialyse.backend.infrastructure.security.CenterAccessGuard;
import com.hemodialyse.backend.infrastructure.security.UserPrincipal;
import org.springframework.format.annotation.DateTimeFormat;
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
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.UUID;

/**
 * Impression du cahier de dialyse d'un patient du centre courant (modèle de document {@code CAHIER_DIALYSE}).
 */
@RestController
@RequestMapping("/api/v1/cahier-dialyse")
@PreAuthorize("hasAnyRole('ADMIN','SECRETAIRE','MEDECIN','INFIRMIER')")
public class CahierDialyseRestController {

    private final CahierDialyseReportService rapport;
    private final CenterAccessGuard centerAccessGuard;

    public CahierDialyseRestController(CahierDialyseReportService rapport, CenterAccessGuard centerAccessGuard) {
        this.rapport = rapport;
        this.centerAccessGuard = centerAccessGuard;
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
     * Cahier imprimable : une page par séance validée, signée ou facturée, éventuellement limitée à une période.
     * {@code tz} = fuseau IANA du navigateur pour la date d'édition (UTC à défaut ou si invalide).
     */
    @GetMapping("/{patientId}/imprimer")
    public ResponseEntity<byte[]> imprimer(
            @PathVariable UUID patientId,
            @RequestParam(required = false) UUID centerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String tz,
            Authentication authentication) {
        UUID centre = centerAccessGuard.requireCenter(centerId).value();
        String editePar = ((UserPrincipal) authentication.getPrincipal()).getUsername();
        ModeleDocumentPrinter.Document doc = rapport.imprimer(centre, patientId, from, to, zone(tz), editePar);
        String base = "cahier-dialyse-" + patientId.toString().substring(0, 8);
        return switch (doc.format().toUpperCase(Locale.ROOT)) {
            case "EXCEL", "XLS", "XLSX" -> ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                            .filename(base + ".xlsx", StandardCharsets.UTF_8).build().toString())
                    .body(doc.content());
            case "HTML" -> ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(doc.content());
            default -> ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                            .filename(base + ".pdf", StandardCharsets.UTF_8).build().toString())
                    .body(doc.content());
        };
    }
}
