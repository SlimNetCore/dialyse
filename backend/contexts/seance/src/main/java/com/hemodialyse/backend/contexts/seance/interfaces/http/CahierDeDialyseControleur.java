package com.hemodialyse.backend.contexts.seance.interfaces.http;

import com.hemodialyse.backend.contexts.seance.application.CahierImprime;
import com.hemodialyse.backend.contexts.seance.application.ImprimerLeCahierDeDialyse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Adapter entrant HTTP de l'impression du cahier de dialyse (PL5, ex {@code CahierDialyseRestController}) : MEME URL, MEMES droits (ADMIN,
 * SECRETAIRE, MEDECIN, INFIRMIER), MEMES en-têtes de réponse. Écarts décidés (ADR 0144) : entrée illisible 400 {@code BAD_REQUEST} et verbe
 * non supporté 405 (KB-PL5-1), patient introuvable 404 (KB-PL5-6e), impression journalisée (KB-PL5-3), volet médical masqué à la
 * secrétaire (KB-PL5-4).
 */
@RestController
@RequestMapping("/api/v1/cahier-dialyse")
@PreAuthorize("hasAnyRole('ADMIN','SECRETAIRE','MEDECIN','INFIRMIER')")
public class CahierDeDialyseControleur {

    private final ImprimerLeCahierDeDialyse imprimer;

    public CahierDeDialyseControleur(ImprimerLeCahierDeDialyse imprimer) {
        this.imprimer = imprimer;
    }

    @GetMapping("/{patientId}/imprimer")
    public ResponseEntity<byte[]> imprimer(@PathVariable UUID patientId, @RequestParam(required = false) UUID centerId,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                           @RequestParam(required = false) String tz) {
        CahierImprime cahier = imprimer.executer(centerId, patientId, from, to, tz);
        String base = "cahier-dialyse-" + patientId.toString().substring(0, 8);
        return switch (cahier.format()) {
            case EXCEL -> ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                            .filename(base + ".xlsx", StandardCharsets.UTF_8).build().toString())
                    .body(cahier.octets());
            case HTML -> ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(cahier.octets());
            case PDF -> ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                            .filename(base + ".pdf", StandardCharsets.UTF_8).build().toString())
                    .body(cahier.octets());
        };
    }
}
