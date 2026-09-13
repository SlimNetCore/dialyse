package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.domain.medical.serologie.aggregate.Serologie;
import com.hemodialyse.backend.domain.medical.serologie.port.SerologieUseCase;
import com.hemodialyse.backend.domain.medical.serologie.valueobject.MarqueurSerologique;
import com.hemodialyse.backend.domain.medical.serologie.valueobject.ResultatSerologique;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.infrastructure.security.CenterAccessGuard;
import com.hemodialyse.backend.infrastructure.web.dto.request.CreateSerologieRequest;
import com.hemodialyse.backend.infrastructure.web.dto.request.UpdateSerologieRequest;
import com.hemodialyse.backend.infrastructure.web.dto.response.PagedResponse;
import com.hemodialyse.backend.infrastructure.web.dto.response.SerologieResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Résultats sérologiques du patient (VIH, hépatites B/C, syphilis...).
 * <p>
 * Accès réservé au corps médical : le MEDECIN écrit, l'ADMIN consulte.
 */
@RestController
@RequestMapping("/api/v1/patients")
public class SerologieRestController {

    private final SerologieUseCase useCase;
    private final CenterAccessGuard centerAccessGuard;

    public SerologieRestController(SerologieUseCase useCase, CenterAccessGuard centerAccessGuard) {
        this.useCase = useCase;
        this.centerAccessGuard = centerAccessGuard;
    }

    @PreAuthorize("hasAnyRole('MEDECIN','ADMIN')")
    @GetMapping("/{patientId}/serologies")
    public ResponseEntity<PagedResponse<SerologieResponse>> list(
            @PathVariable UUID patientId,
            @RequestParam(required = false) UUID centerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        CenterId center = centerAccessGuard.requireCenter(centerId);
        var paged = useCase.listPagedByPatient(center, patientId, page, size);
        return ResponseEntity.ok(PagedResponse.from(paged, SerologieResponse::from));
    }

    /**
     * Bandeau « statut sérologique » : le dernier résultat connu pour chaque marqueur.
     */
    @PreAuthorize("hasAnyRole('MEDECIN','ADMIN')")
    @GetMapping("/{patientId}/serologies/synthese")
    public ResponseEntity<List<SerologieResponse>> synthese(@PathVariable UUID patientId,
                                                            @RequestParam(required = false) UUID centerId) {
        CenterId center = centerAccessGuard.requireCenter(centerId);
        return ResponseEntity.ok(useCase.listDerniersResultatsByPatient(center, patientId).stream()
                .map(SerologieResponse::from).toList());
    }

    @PreAuthorize("hasRole('MEDECIN')")
    @PostMapping("/{patientId}/serologies")
    public ResponseEntity<SerologieResponse> create(@PathVariable UUID patientId,
                                                    @RequestBody @Valid CreateSerologieRequest request) {
        CenterId center = centerAccessGuard.requireCenter(request.centerId());
        Serologie serologie = useCase.create(center, patientId, MarqueurSerologique.valueOf(request.marqueur()),
                ResultatSerologique.valueOf(request.resultat()), request.titre(), request.unite(),
                request.datePrelevement(), request.laboratoire(), request.dateProchainControle(),
                request.conduiteATenir());
        return ResponseEntity.ok(SerologieResponse.from(serologie));
    }

    @PreAuthorize("hasRole('MEDECIN')")
    @PutMapping("/{patientId}/serologies/{serologieId}")
    public ResponseEntity<SerologieResponse> update(@PathVariable UUID patientId,
                                                    @PathVariable UUID serologieId,
                                                    @RequestBody @Valid UpdateSerologieRequest request) {
        CenterId center = centerAccessGuard.requireCenter(request.centerId());
        Serologie serologie = useCase.update(center, patientId, serologieId,
                ResultatSerologique.valueOf(request.resultat()), request.conduiteATenir());
        return ResponseEntity.ok(SerologieResponse.from(serologie));
    }

    @PreAuthorize("hasRole('MEDECIN')")
    @DeleteMapping("/{patientId}/serologies/{serologieId}")
    public ResponseEntity<Void> delete(@PathVariable UUID patientId,
                                       @PathVariable UUID serologieId,
                                       @RequestParam(required = false) UUID centerId) {
        CenterId center = centerAccessGuard.requireCenter(centerId);
        useCase.delete(center, patientId, serologieId);
        return ResponseEntity.noContent().build();
    }
}
