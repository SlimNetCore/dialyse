package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http;

import com.hemodialyse.backend.contexts.dossiermedical.application.AntecedentUseCases;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Antecedent;
import com.hemodialyse.backend.contexts.dossiermedical.domain.AntecedentId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.ConceptCode;
import com.hemodialyse.backend.contexts.dossiermedical.domain.TypeAntecedent;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/dossier-medical/antecedents")
public class AntecedentsControleur {

    private final AntecedentUseCases useCases;

    public AntecedentsControleur(AntecedentUseCases useCases) {
        this.useCases = useCases;
    }

    @GetMapping
    public ResponseEntity<PagedResult<Antecedent>> lister(
            @RequestParam UUID centreId,
            @RequestParam UUID patientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(useCases.listerParPatient(new CentreId(centreId), patientId, page, size));
    }

    @GetMapping("/{antecedentId}")
    public ResponseEntity<Antecedent> consulter(
            @PathVariable UUID antecedentId,
            @RequestParam UUID centreId) {
        return ResponseEntity.ok(useCases.consulter(new AntecedentId(antecedentId), new CentreId(centreId)));
    }

    @PostMapping
    public ResponseEntity<AntecedentId> enregistrer(@RequestBody EnregistrerRequest req) {
        AntecedentId id = useCases.enregistrer(new AntecedentUseCases.EnregistrerAntecedentCommande(
                req.patientId(), new CentreId(req.centreId()), TypeAntecedent.valueOf(req.type()),
                req.diagnosticSystem() == null || req.diagnosticCode() == null ? null
                        : new ConceptCode(req.diagnosticSystem(), req.diagnosticCode(), req.diagnosticDisplay()),
                req.libelleLibre(), req.dateDebut(), req.dateFin(), req.severite(), req.note()), OffsetDateTime.now());
        return ResponseEntity.ok(id);
    }

    @PutMapping("/{antecedentId}")
    public ResponseEntity<Antecedent> modifier(
            @PathVariable UUID antecedentId,
            @RequestParam UUID centreId,
            @RequestBody ModifierRequest req) {
        Antecedent modifie = useCases.modifier(new AntecedentUseCases.ModifierAntecedentCommande(
                new AntecedentId(antecedentId), new CentreId(centreId),
                req.diagnosticSystem() == null || req.diagnosticCode() == null ? null
                        : new ConceptCode(req.diagnosticSystem(), req.diagnosticCode(), req.diagnosticDisplay()),
                req.libelleLibre(), req.dateDebut(), req.dateFin(),
                req.statutClinique() == null ? null
                        : AntecedentUseCases.StatutCliniqueParam.valueOf(req.statutClinique()),
                req.severite(), req.note()), OffsetDateTime.now());
        return ResponseEntity.ok(modifie);
    }

    @DeleteMapping("/{antecedentId}")
    public ResponseEntity<Void> supprimer(
            @PathVariable UUID antecedentId,
            @RequestParam UUID centreId) {
        useCases.supprimer(new AntecedentId(antecedentId), new CentreId(centreId));
        return ResponseEntity.noContent().build();
    }

    // ---------- DTOs de requête ----------

    public record EnregistrerRequest(UUID patientId, UUID centreId, String type,
                                     String diagnosticSystem, String diagnosticCode, String diagnosticDisplay,
                                     String libelleLibre, LocalDate dateDebut, LocalDate dateFin,
                                     String severite, String note) {
    }

    public record ModifierRequest(String diagnosticSystem, String diagnosticCode, String diagnosticDisplay,
                                  String libelleLibre, LocalDate dateDebut, LocalDate dateFin,
                                  String statutClinique, String severite, String note) {
    }
}
