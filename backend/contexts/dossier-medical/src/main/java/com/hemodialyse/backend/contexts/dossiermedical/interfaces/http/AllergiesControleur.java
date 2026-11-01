package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http;

import com.hemodialyse.backend.contexts.dossiermedical.application.AllergieUseCases;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Allergie;
import com.hemodialyse.backend.contexts.dossiermedical.domain.AllergieId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.TypeAllergie;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/dossier-medical/allergies")
public class AllergiesControleur {

    private final AllergieUseCases useCases;

    public AllergiesControleur(AllergieUseCases useCases) {
        this.useCases = useCases;
    }

    @GetMapping
    public ResponseEntity<PagedResult<Allergie>> lister(
            @RequestParam UUID centreId,
            @RequestParam UUID patientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(useCases.listerParPatient(new CentreId(centreId), patientId, page, size));
    }

    @GetMapping("/{allergieId}")
    public ResponseEntity<Allergie> consulter(
            @PathVariable UUID allergieId,
            @RequestParam UUID centreId) {
        return ResponseEntity.ok(useCases.consulter(new AllergieId(allergieId), new CentreId(centreId)));
    }

    @PostMapping
    public ResponseEntity<AllergieId> enregistrer(@RequestBody EnregistrerRequest req) {
        AllergieId id = useCases.enregistrer(new AllergieUseCases.EnregistrerAllergieCommande(
                req.patientId(), new CentreId(req.centreId()), req.allergene(),
                TypeAllergie.valueOf(req.type()), req.reaction(), req.dateDebut(), req.dateFin(),
                req.severite(), req.note()), OffsetDateTime.now());
        return ResponseEntity.ok(id);
    }

    @PutMapping("/{allergieId}")
    public ResponseEntity<Allergie> modifier(
            @PathVariable UUID allergieId,
            @RequestParam UUID centreId,
            @RequestBody ModifierRequest req) {
        Allergie modifie = useCases.modifier(new AllergieUseCases.ModifierAllergieCommande(
                new AllergieId(allergieId), new CentreId(centreId), req.reaction(),
                req.dateDebut(), req.dateFin(),
                req.statutClinique() == null ? null
                        : AllergieUseCases.StatutCliniqueParam.valueOf(req.statutClinique()),
                req.severite(), req.note()), OffsetDateTime.now());
        return ResponseEntity.ok(modifie);
    }

    @DeleteMapping("/{allergieId}")
    public ResponseEntity<Void> supprimer(
            @PathVariable UUID allergieId,
            @RequestParam UUID centreId) {
        useCases.supprimer(new AllergieId(allergieId), new CentreId(centreId));
        return ResponseEntity.noContent().build();
    }

    // ---------- DTOs de requête ----------

    public record EnregistrerRequest(UUID patientId, UUID centreId, String allergene, String type,
                                     String reaction, LocalDate dateDebut, LocalDate dateFin,
                                     String severite, String note) {
    }

    public record ModifierRequest(String reaction, LocalDate dateDebut, LocalDate dateFin,
                                  String statutClinique, String severite, String note) {
    }
}
