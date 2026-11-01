package com.hemodialyse.backend.contexts.patientcouverture.interfaces.http;

import com.hemodialyse.backend.contexts.patientcouverture.application.PatientUseCases;
import com.hemodialyse.backend.contexts.patientcouverture.domain.Identite;
import com.hemodialyse.backend.contexts.patientcouverture.domain.Patient;
import com.hemodialyse.backend.contexts.patientcouverture.domain.PatientId;
import com.hemodialyse.backend.contexts.patientcouverture.interfaces.http.dto.AdmettreRequest;
import com.hemodialyse.backend.contexts.patientcouverture.interfaces.http.dto.SortieRequest;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/patient-couverture/patients")
public class PatientsControleur {
    private final PatientUseCases useCases;

    public PatientsControleur(PatientUseCases useCases) {
        this.useCases = useCases;
    }

    @PostMapping
    public ResponseEntity<PatientId> admettre(@RequestBody AdmettreRequest req) {
        PatientId id = useCases.admettre(new PatientUseCases.AdmettrePatientCommande(
                new CentreId(req.centreId()), new Identite(req.nom(), req.prenom(), req.sexe(), req.dateNaissance()),
                req.numeroAssurance(), req.dateAdmission(), req.auteurId()), Instant.now());
        return ResponseEntity.ok(id);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Patient> consulter(@PathVariable UUID id, @RequestParam UUID centreId) {
        return ResponseEntity.ok(useCases.consulter(new PatientId(id), new CentreId(centreId)));
    }

    @GetMapping
    public ResponseEntity<PagedResult<Patient>> lister(@RequestParam UUID centreId,
                                                       @RequestParam(defaultValue = "0") int page,
                                                       @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(useCases.lister(new CentreId(centreId), page, size));
    }

    @PostMapping("/{id}/sortie")
    public ResponseEntity<Void> sortie(@PathVariable UUID id, @RequestBody SortieRequest req) {
        useCases.declarerSortie(new PatientUseCases.SortirPatientCommande(new PatientId(id), new CentreId(req.centreId()),
                req.dateSortie(), req.auteurId()), Instant.now());
        return ResponseEntity.noContent().build();
    }
}