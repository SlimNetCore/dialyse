package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http;

import com.hemodialyse.backend.contexts.dossiermedical.application.BilanPreGreffeUseCases;
import com.hemodialyse.backend.contexts.dossiermedical.domain.BilanPreGreffe;
import com.hemodialyse.backend.contexts.dossiermedical.domain.BilanPreGreffeId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.EtapeBilan;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/dossier-medical/bilans-pregreffe")
public class BilansPreGreffeControleur {

    private final BilanPreGreffeUseCases useCases;

    public BilansPreGreffeControleur(BilanPreGreffeUseCases useCases) {
        this.useCases = useCases;
    }

    @GetMapping
    public ResponseEntity<PagedResult<BilanPreGreffe>> lister(
            @RequestParam UUID centreId,
            @RequestParam UUID patientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(useCases.listerParPatient(new CentreId(centreId), patientId, page, size));
    }

    @GetMapping("/{bilanId}")
    public ResponseEntity<BilanPreGreffe> consulter(
            @PathVariable UUID bilanId,
            @RequestParam UUID centreId) {
        return ResponseEntity.ok(useCases.consulter(new BilanPreGreffeId(bilanId), new CentreId(centreId)));
    }

    @PostMapping
    public ResponseEntity<BilanPreGreffeId> demarrer(
            @RequestBody DemarrerRequest req) {
        List<EtapeBilan> etapes = req.etapes() == null ? List.of() : req.etapes().stream()
                .map(e -> EtapeBilan.aFaire(e.code(), e.libelle()))
                .toList();
        BilanPreGreffeId id = useCases.demarrer(
                new BilanPreGreffeUseCases.DemarrerBilanCommande(
                        req.patientId(), new CentreId(req.centreId()), etapes,
                        req.dateDemarrage(), req.commentaire()), Instant.now());
        return ResponseEntity.ok(id);
    }

    @PostMapping("/{bilanId}/etapes/{codeEtape}/validation")
    public ResponseEntity<BilanPreGreffe> validerEtape(
            @PathVariable UUID bilanId,
            @PathVariable String codeEtape,
            @RequestBody ValiderEtapeRequest req) {
        return ResponseEntity.ok(useCases.validerEtape(new BilanPreGreffeId(bilanId),
                new CentreId(req.centreId()), codeEtape, req.validePar(), Instant.now()));
    }

    @PostMapping("/{bilanId}/completion")
    public ResponseEntity<BilanPreGreffe> completer(
            @PathVariable UUID bilanId,
            @RequestBody CentreRequest req) {
        return ResponseEntity.ok(useCases.completer(new BilanPreGreffeId(bilanId),
                new CentreId(req.centreId()), Instant.now()));
    }

    @PostMapping("/{bilanId}/annulation")
    public ResponseEntity<BilanPreGreffe> annuler(
            @PathVariable UUID bilanId,
            @RequestBody CentreRequest req) {
        return ResponseEntity.ok(useCases.annuler(new BilanPreGreffeId(bilanId),
                new CentreId(req.centreId()), Instant.now()));
    }

    // ---------- DTOs de requête ----------

    public record EtapeRequest(String code, String libelle) {
    }

    public record DemarrerRequest(UUID patientId, UUID centreId, List<EtapeRequest> etapes,
                                  LocalDate dateDemarrage, String commentaire) {
    }

    public record ValiderEtapeRequest(UUID centreId, String validePar) {
    }

    public record CentreRequest(UUID centreId) {
    }
}
