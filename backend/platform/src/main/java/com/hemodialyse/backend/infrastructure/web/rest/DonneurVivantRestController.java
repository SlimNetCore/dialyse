package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.domain.medical.greffe.port.DonneurVivantUseCase;
import com.hemodialyse.backend.domain.medical.greffe.valueobject.LienParenteDonneur;
import com.hemodialyse.backend.domain.medical.greffe.valueobject.ResultatCrossmatch;
import com.hemodialyse.backend.domain.medical.greffe.valueobject.StatutBilanDonneur;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.infrastructure.security.CenterAccessGuard;
import com.hemodialyse.backend.infrastructure.web.dto.request.CreateDonneurVivantRequest;
import com.hemodialyse.backend.infrastructure.web.dto.request.UpdateDonneurVivantRequest;
import com.hemodialyse.backend.infrastructure.web.dto.response.DonneurVivantResponse;
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
 * Candidats donneurs vivants pour la greffe rénale d'un patient receveur : identité, lien de
 * parenté, bilan de compatibilité (groupe sanguin, typage HLA, crossmatch), décision finale.
 * <p>
 * Accès réservé au corps médical : le MEDECIN écrit, l'ADMIN consulte.
 */
@RestController
@RequestMapping("/api/v1/patients")
public class DonneurVivantRestController {

    private final DonneurVivantUseCase useCase;
    private final CenterAccessGuard centerAccessGuard;

    public DonneurVivantRestController(DonneurVivantUseCase useCase, CenterAccessGuard centerAccessGuard) {
        this.useCase = useCase;
        this.centerAccessGuard = centerAccessGuard;
    }

    @PreAuthorize("hasAnyRole('MEDECIN','ADMIN')")
    @GetMapping("/{patientId}/greffe/donneurs")
    public ResponseEntity<List<DonneurVivantResponse>> list(@PathVariable UUID patientId,
                                                            @RequestParam(required = false) UUID centerId) {
        CenterId center = centerAccessGuard.requireCenter(centerId);
        return ResponseEntity.ok(useCase.listByPatient(center, patientId).stream()
                .map(DonneurVivantResponse::from).toList());
    }

    @PreAuthorize("hasRole('MEDECIN')")
    @PostMapping("/{patientId}/greffe/donneurs")
    public ResponseEntity<DonneurVivantResponse> create(@PathVariable UUID patientId,
                                                        @RequestBody CreateDonneurVivantRequest request) {
        CenterId center = centerAccessGuard.requireCenter(request.centerId());
        var donneur = useCase.create(center, patientId, request.nom(), request.prenom(), request.dateNaissance(),
                LienParenteDonneur.valueOf(request.lienParente()), request.telephone(), request.groupeSanguin(),
                request.typageHla());
        return ResponseEntity.ok(DonneurVivantResponse.from(donneur));
    }

    @PreAuthorize("hasRole('MEDECIN')")
    @PutMapping("/{patientId}/greffe/donneurs/{donneurId}")
    public ResponseEntity<DonneurVivantResponse> update(@PathVariable UUID patientId, @PathVariable UUID donneurId,
                                                        @RequestBody UpdateDonneurVivantRequest request) {
        CenterId center = centerAccessGuard.requireCenter(request.centerId());
        var donneur = useCase.update(center, patientId, donneurId, request.nom(), request.prenom(),
                request.dateNaissance(), LienParenteDonneur.valueOf(request.lienParente()), request.telephone(),
                request.groupeSanguin(), request.typageHla(), StatutBilanDonneur.valueOf(request.statutBilan()),
                ResultatCrossmatch.valueOf(request.crossmatchResultat()), request.dateCrossmatch(),
                request.bilanRealise(), request.contreIndications(), request.decisionFinale(),
                request.dateDecision());
        return ResponseEntity.ok(DonneurVivantResponse.from(donneur));
    }

    @PreAuthorize("hasRole('MEDECIN')")
    @DeleteMapping("/{patientId}/greffe/donneurs/{donneurId}")
    public ResponseEntity<Void> delete(@PathVariable UUID patientId, @PathVariable UUID donneurId,
                                       @RequestParam(required = false) UUID centerId) {
        CenterId center = centerAccessGuard.requireCenter(centerId);
        useCase.delete(center, patientId, donneurId);
        return ResponseEntity.noContent().build();
    }
}
