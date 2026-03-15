package com.hemodialyse.backend.infrastructure.web;

import com.hemodialyse.backend.application.patient.PatientService;
import com.hemodialyse.backend.domain.patient.Patient;
import com.hemodialyse.backend.domain.patient.PatientRepository;
import com.hemodialyse.backend.domain.patient.PatientType;
import com.hemodialyse.backend.domain.shared.TenantScope;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/v1/patients")
public class PatientController {

    private final PatientService service;
    private final PatientRepository patientRepo;

    public PatientController(PatientService service, PatientRepository patientRepo) {
        this.service = service;
        this.patientRepo = patientRepo;
    }

    // For this bootstrap we accept a very small payload
    record CreatePatientRequest(
        String nom, String prenom, String sexe, LocalDate dateAdmission, LocalDate dateNaissance,
        String numeroAssurance, PatientType typePatient, LocalDate attestationDebut, LocalDate attestationFin,
        UUID centerId, String userId,
        // Extended fields
        String codePatient, String civilite, String groupeSanguin, Integer nombreEnfants,
        Boolean enSommeil, String lieuNaissance, String situationFamiliale,
        String profession1, String profession2, String adresse,
        String telPersonnel, String telMobile, String telBureau, String email,
        String etatPatient, String qualiteAssure, String observation, Boolean sousKt,
        String photoBase64,
        UUID centrePayeurId, UUID medecinTraitantId, UUID salleId, UUID positionId,
        UUID transporteurAllerId, UUID transporteurRetourId, UUID categorieTransportId,
        Boolean jourDimanche, Boolean jourLundi, Boolean jourMardi, Boolean jourMercredi,
        Boolean jourJeudi, Boolean jourVendredi, Boolean jourSamedi,
        String assureNom, String assurePrenom, String assureSexe, LocalDate assureDateNaissance,
        String assureTelPersonnel, String assureAdresse, String assureGroupeSanguin
    ) {}

    @PostMapping
    public ResponseEntity<?> create(@RequestBody @Valid CreatePatientRequest req) {
        TenantScope scope = new TenantScope(req.centerId(), req.userId(), Set.of("AGENT_ADMISSION"));

        Patient p = service.createPatient(scope, req.nom(), req.prenom(), req.sexe(),
            req.dateAdmission(), req.dateNaissance(), req.numeroAssurance(),
            req.typePatient() == null ? PatientType.NON_VACANCIER : req.typePatient(),
            req.attestationDebut(), req.attestationFin());

        // Set extended fields
        p.setCodePatient(req.codePatient());
        p.setCivilite(req.civilite());
        p.setGroupeSanguin(req.groupeSanguin());
        p.setNombreEnfants(req.nombreEnfants());
        p.setEnSommeil(req.enSommeil());
        p.setLieuNaissance(req.lieuNaissance());
        p.setSituationFamiliale(req.situationFamiliale());
        p.setProfession1(req.profession1());
        p.setProfession2(req.profession2());
        p.setAdresse(req.adresse());
        p.setTelPersonnel(req.telPersonnel());
        p.setTelMobile(req.telMobile());
        p.setTelBureau(req.telBureau());
        p.setEmail(req.email());
        p.setEtatPatient(req.etatPatient());
        p.setQualiteAssure(req.qualiteAssure());
        p.setObservation(req.observation());
        p.setSousKt(req.sousKt());
        p.setPhotoBase64(req.photoBase64());
        p.setCentrePayeurId(req.centrePayeurId());
        p.setMedecinTraitantId(req.medecinTraitantId());
        p.setSalleId(req.salleId());
        p.setPositionId(req.positionId());
        p.setTransporteurAllerId(req.transporteurAllerId());
        p.setTransporteurRetourId(req.transporteurRetourId());
        p.setCategorieTransportId(req.categorieTransportId());
        p.setJourDimanche(req.jourDimanche());
        p.setJourLundi(req.jourLundi());
        p.setJourMardi(req.jourMardi());
        p.setJourMercredi(req.jourMercredi());
        p.setJourJeudi(req.jourJeudi());
        p.setJourVendredi(req.jourVendredi());
        p.setJourSamedi(req.jourSamedi());
        p.setAssureNom(req.assureNom());
        p.setAssurePrenom(req.assurePrenom());
        p.setAssureSexe(req.assureSexe());
        p.setAssureDateNaissance(req.assureDateNaissance());
        p.setAssureTelPersonnel(req.assureTelPersonnel());
        p.setAssureAdresse(req.assureAdresse());
        p.setAssureGroupeSanguin(req.assureGroupeSanguin());

        patientRepo.save(p);

        return ResponseEntity.ok(Map.of("id", p.getId(), "centerId", p.getCenterId(), "typePatient", p.getTypePatient()));
    }

    @GetMapping
    public ResponseEntity<List<Patient>> list(@RequestParam UUID centerId, @RequestParam String userId) {
        return ResponseEntity.ok(patientRepo.findByCenterId(centerId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable UUID id, @RequestParam UUID centerId, @RequestParam String userId) {
        TenantScope scope = new TenantScope(centerId, userId, Set.of("LECTURE"));
        Optional<Patient> p = service.findById(scope, id);
        return p.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }
}
