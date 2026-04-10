package com.hemodialyse.backend.application.web;

import com.hemodialyse.backend.application.notification.NotificationService;
import com.hemodialyse.backend.domain.patient.port.PatientUseCase;
import com.hemodialyse.backend.domain.patient.port.PatientUseCase.CreatePatientCommand;
import com.hemodialyse.backend.domain.patient.model.Patient;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.domain.pec.port.PecUseCase;
import com.hemodialyse.backend.domain.pec.model.PecStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/v1/patients")
public class PatientRestController {

    private final PatientUseCase useCase;
    private final NotificationService notificationService;
    private final PecUseCase pecUseCase;

    public PatientRestController(PatientUseCase useCase, NotificationService notificationService, PecUseCase pecUseCase) {
        this.useCase = useCase;
        this.notificationService = notificationService;
        this.pecUseCase = pecUseCase;
    }

    record CreatePatientRequest(
        UUID centerId, String userId,
        // Step 1
        String civilite, String nom, String prenom, String sexe, String groupeSanguin, int nombreEnfants,
        LocalDate dateAdmission, LocalDate dateNaissance, String lieuNaissance, String situationFamiliale,
        String profession, String adresse, String telPersonnel, String telMobile, String telBureau, String email,
        boolean sousKt, boolean epoEnabled, LocalDate epoDate, boolean ferEnabled, LocalDate ferDate,
        String observation, String qualiteAssure, String photoBase64, boolean enSommeil,
        // Step 2
        String numeroAssurance, UUID centrePayeurId,
        String assureSexe, String assureNom, String assurePrenom, String assureDateNaissance,
        String assureTelPersonnel, String assureAdresse, String assureGroupeSanguin,
        String assureTelMobile, String assureTelBureau,
        // Step 3
        UUID medecinTraitantId, UUID salleId, UUID positionId,
        UUID transporteurAllerId, UUID transporteurRetourId, UUID categorieTransportId, String etatPatient,
        LocalDate dateEvenementEtat,
        boolean jourDimanche, boolean jourLundi, boolean jourMardi,
        boolean jourMercredi, boolean jourJeudi, boolean jourVendredi, boolean jourSamedi,
        // Step 4
        UUID attestationId, LocalDate attestationDebut, LocalDate attestationFin,
        // Step 5
        UUID pecId, LocalDate pecDateDebutDemande, LocalDate pecDateFinDemande, UUID pecForfaitDemandeId
    ) {}

    private CreatePatientCommand toCommand(CreatePatientRequest r) {
        return new CreatePatientCommand(
            r.civilite(), r.nom(), r.prenom(), r.sexe(), r.groupeSanguin(), r.nombreEnfants(),
            r.dateAdmission(), r.dateNaissance(), r.lieuNaissance(), r.situationFamiliale(),
            r.profession(), r.adresse(), r.telPersonnel(), r.telMobile(), r.telBureau(), r.email(),
            r.sousKt(), r.epoEnabled(), r.epoDate(), r.ferEnabled(), r.ferDate(),
            r.observation(), r.qualiteAssure(), r.photoBase64(), r.enSommeil(),
            r.numeroAssurance(), r.centrePayeurId(),
            r.assureSexe(), r.assureNom(), r.assurePrenom(), r.assureDateNaissance(),
            r.assureTelPersonnel(), r.assureAdresse(), r.assureGroupeSanguin(),
            r.assureTelMobile(), r.assureTelBureau(),
            r.medecinTraitantId(), r.salleId(), r.positionId(),
            r.transporteurAllerId(), r.transporteurRetourId(), r.categorieTransportId(), r.etatPatient(),
            r.dateEvenementEtat(),
            r.jourDimanche(), r.jourLundi(), r.jourMardi(),
            r.jourMercredi(), r.jourJeudi(), r.jourVendredi(), r.jourSamedi(),
            r.attestationId(), r.attestationDebut(), r.attestationFin(),
            r.pecId(), r.pecDateDebutDemande(), r.pecDateFinDemande(), r.pecForfaitDemandeId()
        );
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody CreatePatientRequest r) {
        Patient p = useCase.createPatient(CenterId.of(r.centerId()), toCommand(r));

        // Send real-time notification
        notificationService.notifyPatientCreated(
            r.centerId(), p.getCodePatient(), r.nom(), r.prenom()
        );

        return ResponseEntity.ok(Map.of(
            "id", p.getId().value(), "centerId", p.getCenterId().value(),
            "typePatient", p.getTypePatient(), "codePatient", p.getCodePatient()
        ));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable UUID id, @RequestBody CreatePatientRequest r) {
        Patient p = useCase.updatePatient(CenterId.of(r.centerId()), id, toCommand(r));
        return ResponseEntity.ok(Map.of(
            "id", p.getId().value(), "centerId", p.getCenterId().value(),
            "typePatient", p.getTypePatient(), "codePatient", p.getCodePatient()
        ));
    }

    @GetMapping
    public ResponseEntity<?> list(@RequestParam UUID centerId, @RequestParam String userId) {
        var center = CenterId.of(centerId);
        var rows = useCase.listPatients(center).stream().map(p -> {
            var pecs = pecUseCase.listByPatient(center, p.getId().value());
            boolean facturable = pecs.stream().anyMatch(pc -> pc.getStatus() == PecStatus.VALIDEE);
            return Map.of(
                "id", p.getId().value(),
                "codePatient", p.getCodePatient(),
                "nom", p.getNom(),
                "prenom", p.getPrenom(),
                "sexe", p.getSexe(),
                "dateAdmission", p.getDateAdmission(),
                "numeroAssurance", p.getNumeroAssurance().value(),
                "etatPatient", p.getEtatPatient(),
                "nonFacturable", !facturable
            );
        }).toList();
        return ResponseEntity.ok(rows);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable UUID id, @RequestParam UUID centerId, @RequestParam String userId) {
        return ResponseEntity.ok(useCase.getPatient(CenterId.of(centerId), id));
    }
}
