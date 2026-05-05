package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.application.notification.NotificationService;
import com.hemodialyse.backend.application.query.PatientListQueryService;
import com.hemodialyse.backend.domain.assure.model.Assure;
import com.hemodialyse.backend.domain.assure.model.AssurePatientAssignment;
import com.hemodialyse.backend.domain.assure.port.AssurePatientRepositoryPort;
import com.hemodialyse.backend.domain.assure.port.AssureRepositoryPort;
import com.hemodialyse.backend.domain.patient.model.Patient;
import com.hemodialyse.backend.domain.patient.port.PatientUseCase;
import com.hemodialyse.backend.domain.patient.port.PatientUseCase.CreatePatientCommand;
import com.hemodialyse.backend.domain.pec.port.PecUseCase;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.infrastructure.web.dto.request.CreatePatientRequest;
import com.hemodialyse.backend.infrastructure.web.dto.request.PatientSearchRequest;
import com.hemodialyse.backend.infrastructure.web.dto.request.UpdateAssignmentRequest;
import com.hemodialyse.backend.infrastructure.web.dto.request.UpdateAssureRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/patients")
public class PatientRestController {

    private final PatientUseCase useCase;
    private final NotificationService notificationService;
    private final PecUseCase pecUseCase;
    private final AssureRepositoryPort assureRepo;
    private final AssurePatientRepositoryPort assurePatientRepo;
    private final PatientListQueryService patientListQueryService;

    public PatientRestController(PatientUseCase useCase, NotificationService notificationService, PecUseCase pecUseCase,
                                 AssureRepositoryPort assureRepo, AssurePatientRepositoryPort assurePatientRepo,
                                 PatientListQueryService patientListQueryService) {
        this.useCase = useCase;
        this.notificationService = notificationService;
        this.pecUseCase = pecUseCase;
        this.assureRepo = assureRepo;
        this.assurePatientRepo = assurePatientRepo;
        this.patientListQueryService = patientListQueryService;
    }

    @GetMapping("/{id}/assures/history")
    public ResponseEntity<?> assureHistory(@PathVariable UUID id, @RequestParam UUID centerId) {
        var rows = assurePatientRepo.findHistory(CenterId.of(centerId), id).stream().map(h -> {
            var a = assureRepo.findByNumeroAssurance(h.getNumeroAssurance()).orElse(null);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", h.getId());
            m.put("patientId", id);
            m.put("numeroAssurance", h.getNumeroAssurance());
            m.put("isPrimary", h.isPrimary());
            m.put("actif", h.isActif());
            m.put("dateAffectation", h.getDateAffectation());
            m.put("dateDebutAffectation", h.getDateDebutAffectation());
            m.put("dateFinAffectation", h.getDateFinAffectation());
            m.put("nom", a != null ? a.getNom() : null);
            m.put("prenom", a != null ? a.getPrenom() : null);
            m.put("sexe", a != null ? a.getSexe() : null);
            return m;
        }).toList();
        return ResponseEntity.ok(rows);
    }

    private CreatePatientCommand toCommand(CreatePatientRequest r) {
        return new CreatePatientCommand(
                r.civilite(), r.nom(), r.prenom(), r.sexe(), r.groupeSanguin(), r.nombreEnfants(),
                r.dateAdmission(), r.dateNaissance(), r.lieuNaissance(), r.situationFamiliale(),
                r.profession(), r.adresse(), r.telPersonnel(), r.telMobile(), r.telBureau(), r.email(),
                r.sousKt(), r.epoEnabled(), r.epoDate(), r.ferEnabled(), r.ferDate(),
                r.observation(), r.qualiteAssure(), r.photoBase64(), r.enSommeil(),
                r.numeroAssurance(), r.centrePayeurId(),
                r.assureNumeroAssurance(),
                r.assureSexe(), r.assureNom(), r.assurePrenom(), r.assureDateNaissance(),
                r.assureTelPersonnel(), r.assureAdresse(), r.assureGroupeSanguin(),
                r.assureTelMobile(), r.assureTelBureau(),
                r.assureHistoryJson(),
                r.piecesJointesJson(),
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
    public ResponseEntity<?> create(@RequestBody @Valid CreatePatientRequest r) {
        Patient p = useCase.createPatient(CenterId.of(r.centerId()), toCommand(r));

        // Send real-time notification
        notificationService.notifyPatientCreated(
                r.centerId(), p.getId().value(), p.getCodePatient(), r.nom(), r.prenom()
        );

        return ResponseEntity.ok(Map.of(
                "id", p.getId().value(), "centerId", p.getCenterId().value(),
                "typePatient", p.getTypePatient(), "codePatient", p.getCodePatient()
        ));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable UUID id, @RequestBody @Valid CreatePatientRequest r) {
        Patient p = useCase.updatePatient(CenterId.of(r.centerId()), id, toCommand(r));

        // Notify connected clients to refresh fiche/list in real-time.
        notificationService.notifyPatientUpdated(
                r.centerId(), p.getId().value(), p.getCodePatient(), r.nom(), r.prenom()
        );

        return ResponseEntity.ok(Map.of(
                "id", p.getId().value(), "centerId", p.getCenterId().value(),
                "typePatient", p.getTypePatient(), "codePatient", p.getCodePatient()
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable UUID id, @RequestParam UUID centerId, @RequestParam String userId) {
        return ResponseEntity.ok(useCase.getPatient(CenterId.of(centerId), id));
    }

    @GetMapping("/assures")
    public ResponseEntity<?> listAssures(@RequestParam UUID centerId, @RequestParam(required = false) String q) {
        var rows = assureRepo.searchByCenter(CenterId.of(centerId), q).stream().map(a -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("numeroAssurance", a.getNumeroAssurance());
            m.put("nom", a.getNom());
            m.put("prenom", a.getPrenom());
            m.put("sexe", a.getSexe());
            m.put("dateNaissance", a.getDateNaissance());
            m.put("telPersonnel", a.getTelPersonnel());
            m.put("telMobile", a.getTelMobile());
            m.put("telBureau", a.getTelBureau());
            m.put("adresse", a.getAdresse());
            m.put("groupeSanguin", a.getGroupeSanguin());
            return m;
        }).toList();
        return ResponseEntity.ok(rows);
    }

    @Transactional
    @PostMapping("/{id}/assures/{numeroAssurance}/affecter")
    public ResponseEntity<?> affecterAssure(@PathVariable UUID id,
                                            @PathVariable String numeroAssurance,
                                            @RequestParam UUID centerId) {
        Patient p = useCase.getPatient(CenterId.of(centerId), id);
        if ("ASSURE_LUI_MEME".equals(p.getQualiteAssure())) {
            throw new IllegalArgumentException("Impossible d'affecter un assuré si le patient est assuré lui-même");
        }
        Assure a = assureRepo.findByNumeroAssurance(numeroAssurance)
                .orElseThrow(() -> new IllegalArgumentException("Assuré introuvable"));

        // Règle métier : la date de début est automatiquement aujourd'hui
        LocalDate dateDebut = LocalDate.now();

        // 1. Clôturer l'affectation primaire active précédente (date de fin = aujourd'hui)
        assurePatientRepo.closePrimary(CenterId.of(centerId), id, dateDebut);

        // 2. Créer la nouvelle affectation primaire
        AssurePatientAssignment ap = new AssurePatientAssignment();
        ap.setPatientId(id);
        ap.setNumeroAssurance(numeroAssurance);
        ap.setCenterId(centerId);
        ap.setPrimary(true);
        ap.setDateAffectation(java.time.OffsetDateTime.now());
        ap.setDateDebutAffectation(dateDebut);
        ap.setDateFinAffectation(null); // actif
        AssurePatientAssignment saved = assurePatientRepo.save(ap);

        return ResponseEntity.ok(Map.of(
                "assigned", true,
                "id", saved.getId(),
                "numeroAssurance", numeroAssurance,
                "dateDebutAffectation", dateDebut,
                "nom", Optional.ofNullable(a.getNom()).orElse(""),
                "prenom", Optional.ofNullable(a.getPrenom()).orElse("")
        ));
    }

    @Transactional
    @PutMapping("/{patientId}/assures/assignments/{assignmentId}")
    public ResponseEntity<?> updateAssureAssignment(@PathVariable UUID patientId,
                                                    @PathVariable UUID assignmentId,
                                                    @RequestParam UUID centerId,
                                                    @RequestBody @Valid UpdateAssignmentRequest req) {
        AssurePatientAssignment assignment = assurePatientRepo.findById(assignmentId)
                .orElseThrow(() -> new IllegalArgumentException("Affectation introuvable: " + assignmentId));

        if (!assignment.getPatientId().equals(patientId) || !assignment.getCenterId().equals(centerId)) {
            return ResponseEntity.status(403).body(Map.of("error", "Accès refusé"));
        }

        LocalDate debut = req.dateDebutAffectation();
        LocalDate fin = req.dateFinAffectation();
        if (debut != null && fin != null && fin.isBefore(debut)) {
            throw new IllegalArgumentException("La date de fin doit être >= à la date de début");
        }

        assignment.setDateDebutAffectation(debut);
        assignment.setDateFinAffectation(fin);
        // Si on remet fin à null → l'affectation redevient active ; s'assurer qu'il n'y a pas autre primaire actif
        if (fin == null && assignment.isPrimary()) {
            // Pas de contrainte de doublon : la contrainte unique DB protège
        }
        assurePatientRepo.save(assignment);

        return ResponseEntity.ok(Map.of("updated", true, "id", assignmentId));
    }

    @PutMapping("/assures/{numeroAssurance}")
    public ResponseEntity<?> updateAssure(@PathVariable String numeroAssurance,
                                          @RequestParam UUID centerId,
                                          @RequestBody @Valid UpdateAssureRequest req) {
        Assure existing = assureRepo.findByNumeroAssurance(numeroAssurance)
                .orElseThrow(() -> new IllegalArgumentException("Assuré introuvable: " + numeroAssurance));
        if (!existing.getCenterId().equals(centerId)) {
            return ResponseEntity.status(403).body(Map.of("error", "Accès refusé"));
        }
        existing.setNom(req.nom());
        existing.setPrenom(req.prenom());
        existing.setSexe(req.sexe());
        existing.setDateNaissance(req.dateNaissance() != null && !req.dateNaissance().isBlank()
                ? LocalDate.parse(req.dateNaissance().substring(0, 10)) : null);
        existing.setTelPersonnel(req.telPersonnel());
        existing.setTelMobile(req.telMobile());
        existing.setTelBureau(req.telBureau());
        existing.setAdresse(req.adresse());
        existing.setGroupeSanguin(req.groupeSanguin());
        Assure saved = assureRepo.save(existing);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("numeroAssurance", saved.getNumeroAssurance());
        m.put("nom", saved.getNom());
        m.put("prenom", saved.getPrenom());
        m.put("sexe", saved.getSexe());
        m.put("dateNaissance", saved.getDateNaissance());
        m.put("telPersonnel", saved.getTelPersonnel());
        m.put("telMobile", saved.getTelMobile());
        m.put("telBureau", saved.getTelBureau());
        m.put("adresse", saved.getAdresse());
        m.put("groupeSanguin", saved.getGroupeSanguin());
        return ResponseEntity.ok(m);
    }

    @PostMapping("/search")
    public ResponseEntity<?> search(@RequestBody @Valid PatientSearchRequest request) {
        return listByCriteria(request);
    }

    private ResponseEntity<?> listByCriteria(PatientSearchRequest request) {
        var result = patientListQueryService.search(request.centerId(), request);
        return ResponseEntity.ok(Map.of(
                "items", result.items(),
                "total", result.total(),
                "page", result.page(),
                "size", result.size()
        ));
    }

}











