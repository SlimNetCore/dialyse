package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.application.infirmier.PresenceInfirmierQueryService;
import com.hemodialyse.backend.application.infirmier.PresenceInfirmierQueryService.ChargePage;
import com.hemodialyse.backend.application.infirmier.RemplacementInfirmierService;
import com.hemodialyse.backend.domain.infirmier.model.Presence.AlertePresence;
import com.hemodialyse.backend.domain.infirmier.model.Presence.Candidat;
import com.hemodialyse.backend.domain.infirmier.model.Presence.ChargeInfirmier;
import com.hemodialyse.backend.domain.infirmier.model.Presence.SemainePresence;
import com.hemodialyse.backend.domain.infirmier.model.RemplacementInfirmier;
import com.hemodialyse.backend.domain.planning.service.PlanningSemaineService;
import com.hemodialyse.backend.infrastructure.reporting.ModeleDocumentPrinter;
import com.hemodialyse.backend.infrastructure.reporting.PresenceInfirmierReportService;
import com.hemodialyse.backend.infrastructure.security.CenterAccessGuard;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Planning de présence des infirmiers : semaine, alertes de sous-effectif, remplaçants possibles, charge mensuelle et
 * affectation des remplaçants. Bornée au centre ({@link CenterAccessGuard}).
 */
@RestController
@RequestMapping("/api/v1/infirmiers/presence")
@PreAuthorize("hasAnyRole('ADMIN','SECRETAIRE','MEDECIN','INFIRMIER')")
public class PresenceInfirmierRestController {

    private static final String ECRITURE = "hasAnyRole('ADMIN','SECRETAIRE')";
    private static final int HORIZON_PAR_DEFAUT = 14;

    private final PresenceInfirmierQueryService presence;
    private final RemplacementInfirmierService remplacements;
    private final PresenceInfirmierReportService rapport;
    private final CenterAccessGuard centerAccessGuard;

    public PresenceInfirmierRestController(PresenceInfirmierQueryService presence,
                                           RemplacementInfirmierService remplacements,
                                           PresenceInfirmierReportService rapport,
                                           CenterAccessGuard centerAccessGuard) {
        this.presence = presence;
        this.remplacements = remplacements;
        this.rapport = rapport;
        this.centerAccessGuard = centerAccessGuard;
    }

    private static YearMonth parseMois(String mois) {
        if (mois == null || mois.isBlank()) return YearMonth.now(ZoneOffset.UTC);
        try {
            return YearMonth.parse(mois);
        } catch (DateTimeException e) {
            throw new IllegalArgumentException("Mois invalide (format attendu : yyyy-MM)");
        }
    }

    @GetMapping("/semaine")
    public ResponseEntity<SemainePresence> semaine(
            @RequestParam(required = false) UUID centerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        UUID centre = centerAccessGuard.requireCenter(centerId).value();
        LocalDate jour = date != null ? date : LocalDate.now(ZoneOffset.UTC);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(presence.semaine(centre, jour));
    }

    /**
     * @param jours horizon des alertes en jours à partir d'aujourd'hui (UTC), défaut 14
     */
    @GetMapping("/alertes")
    public ResponseEntity<List<AlertePresence>> alertes(
            @RequestParam(required = false) UUID centerId,
            @RequestParam(defaultValue = "" + HORIZON_PAR_DEFAUT) int jours) {
        UUID centre = centerAccessGuard.requireCenter(centerId).value();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(presence.alertes(centre, LocalDate.now(ZoneOffset.UTC), jours));
    }

    @GetMapping("/remplacants")
    public ResponseEntity<List<Candidat>> remplacants(
            @RequestParam(required = false) UUID centerId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam UUID salleId,
            @RequestParam UUID creneauId) {
        UUID centre = centerAccessGuard.requireCenter(centerId).value();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(presence.remplacants(centre, date, salleId, creneauId));
    }

    /**
     * @param mois mois voulu au format {@code yyyy-MM} (défaut : mois courant)
     */
    @GetMapping("/charge")
    public ResponseEntity<ChargeResponse> charge(
            @RequestParam(required = false) UUID centerId,
            @RequestParam(required = false) String mois,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID centre = centerAccessGuard.requireCenter(centerId).value();
        ChargePage charge = presence.charge(centre, parseMois(mois), page, size);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new ChargeResponse(
                charge.page().items(), charge.page().total(), charge.page().page(), charge.page().size(),
                charge.moyenne()));
    }

    /**
     * Planning de présence imprimable de la semaine (modèle de document du centre).
     */
    @GetMapping("/imprimer")
    public ResponseEntity<byte[]> imprimer(
            @RequestParam(required = false) UUID centerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        UUID centre = centerAccessGuard.requireCenter(centerId).value();
        LocalDate jour = date != null ? date : LocalDate.now(ZoneOffset.UTC);
        ModeleDocumentPrinter.Document doc = rapport.imprimer(centre, jour);
        String base = "presence-infirmiers-" + PlanningSemaineService.debutSemaine(jour);
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

    @PostMapping("/remplacements")
    @PreAuthorize(ECRITURE)
    public ResponseEntity<RemplacementResponse> affecter(@RequestParam(required = false) UUID centerId,
                                                         @Valid @RequestBody RemplacementRequest r) {
        UUID centre = centerAccessGuard.requireCenter(centerId).value();
        RemplacementInfirmier cree = remplacements.affecter(centre, r.date(), r.salleId(), r.creneauId(),
                r.infirmierId(), r.remplaceId());
        return ResponseEntity.status(HttpStatus.CREATED).body(RemplacementResponse.de(cree));
    }

    @DeleteMapping("/remplacements/{id}")
    @PreAuthorize(ECRITURE)
    public ResponseEntity<Void> annuler(@RequestParam(required = false) UUID centerId, @PathVariable UUID id) {
        UUID centre = centerAccessGuard.requireCenter(centerId).value();
        remplacements.annuler(centre, id);
        return ResponseEntity.noContent().build();
    }

    public record RemplacementRequest(
            @NotNull LocalDate date,
            @NotNull UUID salleId,
            @NotNull UUID creneauId,
            @NotNull UUID infirmierId,
            UUID remplaceId) {
    }

    public record RemplacementResponse(UUID id, LocalDate date, UUID salleId, UUID creneauId, UUID infirmierId,
                                       UUID remplaceId) {
        static RemplacementResponse de(RemplacementInfirmier r) {
            return new RemplacementResponse(r.id(), r.date(), r.salleId(), r.creneauId(), r.infirmierId(), r.remplaceId());
        }
    }

    public record ChargeResponse(List<ChargeInfirmier> items, long total, int page, int size, double moyenne) {
    }
}
