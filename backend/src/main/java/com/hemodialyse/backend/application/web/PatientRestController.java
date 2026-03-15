package com.hemodialyse.backend.application.web;

import com.hemodialyse.backend.domain.patient.port.PatientUseCase;
import com.hemodialyse.backend.domain.patient.port.PatientUseCase.CreatePatientCommand;
import com.hemodialyse.backend.domain.patient.model.Patient;
import com.hemodialyse.backend.domain.patient.model.PatientType;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/v1/patients")
public class PatientRestController {

    private final PatientUseCase useCase;

    public PatientRestController(PatientUseCase useCase) { this.useCase = useCase; }

    record CreatePatientRequest(
        UUID centerId, String userId,
        // Step 1
        String civilite, String nom, String prenom, String sexe, String groupeSanguin, int nombreEnfants,
        LocalDate dateAdmission, LocalDate dateNaissance, String lieuNaissance, String situationFamiliale,
        String profession, String adresse, String telPersonnel, String telMobile, String telBureau, String email,
        boolean sousKt, boolean epoEnabled, LocalDate epoDate, boolean ferEnabled, LocalDate ferDate,
        String observation, String qualiteAssure, String photoBase64, boolean enSommeil,
        // Step 2
        String numeroAssurance, PatientType typePatient, UUID centrePayeurId,
        String assureSexe, String assureNom, String assurePrenom, String assureDateNaissance,
        String assureTelPersonnel, String assureAdresse, String assureGroupeSanguin,
        String assureTelMobile, String assureTelBureau,
        // Step 3
        UUID medecinTraitantId, UUID salleId, UUID positionId,
        UUID transporteurAllerId, UUID transporteurRetourId, UUID categorieTransportId, String etatPatient,
        boolean jourDimanche, boolean jourLundi, boolean jourMardi,
        boolean jourMercredi, boolean jourJeudi, boolean jourVendredi, boolean jourSamedi,
        // Step 4
        LocalDate attestationDebut, LocalDate attestationFin,
        // Step 5
        LocalDate pecDateDebutDemande, LocalDate pecDateFinDemande, UUID pecForfaitDemandeId
    ) {}

    @PostMapping
    public ResponseEntity<?> create(@RequestBody CreatePatientRequest r) {
        var cmd = new CreatePatientCommand(
            r.civilite(), r.nom(), r.prenom(), r.sexe(), r.groupeSanguin(), r.nombreEnfants(),
            r.dateAdmission(), r.dateNaissance(), r.lieuNaissance(), r.situationFamiliale(),
            r.profession(), r.adresse(), r.telPersonnel(), r.telMobile(), r.telBureau(), r.email(),
            r.sousKt(), r.epoEnabled(), r.epoDate(), r.ferEnabled(), r.ferDate(),
            r.observation(), r.qualiteAssure(), r.photoBase64(), r.enSommeil(),
            r.numeroAssurance(), r.typePatient(), r.centrePayeurId(),
            r.assureSexe(), r.assureNom(), r.assurePrenom(), r.assureDateNaissance(),
            r.assureTelPersonnel(), r.assureAdresse(), r.assureGroupeSanguin(),
            r.assureTelMobile(), r.assureTelBureau(),
            r.medecinTraitantId(), r.salleId(), r.positionId(),
            r.transporteurAllerId(), r.transporteurRetourId(), r.categorieTransportId(), r.etatPatient(),
            r.jourDimanche(), r.jourLundi(), r.jourMardi(),
            r.jourMercredi(), r.jourJeudi(), r.jourVendredi(), r.jourSamedi(),
            r.attestationDebut(), r.attestationFin(),
            r.pecDateDebutDemande(), r.pecDateFinDemande(), r.pecForfaitDemandeId()
        );
        Patient p = useCase.createPatient(CenterId.of(r.centerId()), cmd);
        return ResponseEntity.ok(Map.of(
            "id", p.getId().value(), "centerId", p.getCenterId().value(),
            "typePatient", p.getTypePatient(), "codePatient", p.getCodePatient()
        ));
    }

    @GetMapping
    public ResponseEntity<?> list(@RequestParam UUID centerId, @RequestParam String userId) {
        return ResponseEntity.ok(useCase.listPatients(CenterId.of(centerId)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable UUID id, @RequestParam UUID centerId, @RequestParam String userId) {
        return ResponseEntity.ok(useCase.getPatient(CenterId.of(centerId), id));
    }
}

