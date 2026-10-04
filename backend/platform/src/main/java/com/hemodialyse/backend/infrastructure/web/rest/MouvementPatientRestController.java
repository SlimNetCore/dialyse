package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.application.patient.MouvementPatientQueryService;
import com.hemodialyse.backend.domain.patient.model.MouvementLigne;
import com.hemodialyse.backend.domain.patient.model.MouvementPatient;
import com.hemodialyse.backend.domain.patient.model.TypeMouvementPatient;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.infrastructure.security.CenterAccessGuard;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Suivi des mouvements de patients du centre courant (admissions, séjours temporaires, transferts, décès, greffes,
 * guérisons, libération des places) : liste paginée filtrable en lecture seule.
 */
@RestController
@RequestMapping("/api/v1/mouvements-patients")
public class MouvementPatientRestController {

    private static final String ACCES = "hasAnyRole('ADMIN','SECRETAIRE','MEDECIN','INFIRMIER')";

    private final MouvementPatientQueryService service;
    private final CenterAccessGuard centerAccessGuard;

    public MouvementPatientRestController(MouvementPatientQueryService service, CenterAccessGuard centerAccessGuard) {
        this.service = service;
        this.centerAccessGuard = centerAccessGuard;
    }

    @GetMapping
    @PreAuthorize(ACCES)
    public ResponseEntity<PagedResult<MouvementResponse>> lister(
            @RequestParam(required = false) UUID centerId,
            @RequestParam(required = false) UUID patientId,
            @RequestParam(required = false) TypeMouvementPatient type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID centre = centerAccessGuard.requireCenter(centerId).value();
        PagedResult<MouvementLigne> paged = service.lister(centre, patientId, type, from, to, page, size);
        List<MouvementResponse> items = paged.items().stream().map(MouvementResponse::de).toList();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(PagedResult.of(items, paged.total(), paged.page(), paged.size()));
    }

    public record MouvementResponse(UUID id, UUID patientId, String patientNom, String patientCode,
                                    TypeMouvementPatient type, LocalDate dateEffet, String etatPrecedent,
                                    String etatNouveau, String salle, String creneau, String generateur,
                                    String joursDialyse, boolean automatique, Instant creeLe) {
        static MouvementResponse de(MouvementLigne l) {
            MouvementPatient m = l.mouvement();
            return new MouvementResponse(m.id(), m.patientId(), l.patientNom(), l.patientCode(), m.type(), m.dateEffet(),
                    m.etatPrecedent(), m.etatNouveau(), l.salleNom(), l.creneauLibelle(), l.generateurCode(),
                    m.joursDialyse(), m.automatique(), m.creeLe());
        }
    }
}
