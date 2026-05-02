package com.hemodialyse.backend.application.web;

import com.hemodialyse.backend.application.notification.NotificationService;
import com.hemodialyse.backend.domain.assure.model.Assure;
import com.hemodialyse.backend.domain.assure.model.AssurePatientAssignment;
import com.hemodialyse.backend.domain.assure.port.AssurePatientRepositoryPort;
import com.hemodialyse.backend.domain.assure.port.AssureRepositoryPort;
import com.hemodialyse.backend.domain.patient.model.Patient;
import com.hemodialyse.backend.domain.patient.port.PatientUseCase;
import com.hemodialyse.backend.domain.patient.port.PatientUseCase.CreatePatientCommand;
import com.hemodialyse.backend.domain.pec.model.PecStatus;
import com.hemodialyse.backend.domain.pec.port.PecUseCase;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("/api/v1/patients")
public class PatientRestController {

    private final PatientUseCase useCase;
    private final NotificationService notificationService;
    private final PecUseCase pecUseCase;
    private final AssureRepositoryPort assureRepo;
    private final AssurePatientRepositoryPort assurePatientRepo;

    public PatientRestController(PatientUseCase useCase, NotificationService notificationService, PecUseCase pecUseCase,
                                 AssureRepositoryPort assureRepo, AssurePatientRepositoryPort assurePatientRepo) {
        this.useCase = useCase;
        this.notificationService = notificationService;
        this.pecUseCase = pecUseCase;
        this.assureRepo = assureRepo;
        this.assurePatientRepo = assurePatientRepo;
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
        String numeroAssurance, UUID centrePayeurId, String assureNumeroAssurance,
        String assureSexe, String assureNom, String assurePrenom, String assureDateNaissance,
        String assureTelPersonnel, String assureAdresse, String assureGroupeSanguin,
        String assureTelMobile, String assureTelBureau,
        String assureHistoryJson,
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

    private static LocalDate parseFlexibleDate(String value) {
        if (value == null || value.isBlank()) return null;
        String raw = value.trim();
        if (raw.contains("T")) raw = raw.substring(0, raw.indexOf('T'));
        if (raw.contains(" ")) raw = raw.substring(0, raw.indexOf(' '));
        try {
            return LocalDate.parse(raw);
        } catch (Exception ignored) {
        }
        try {
            return LocalDate.parse(raw, DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        } catch (Exception ignored) {
            return null;
        }
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
                                                    @RequestBody UpdateAssignmentRequest req) {
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
                                          @RequestBody UpdateAssureRequest req) {
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

    record UpdateAssignmentRequest(LocalDate dateDebutAffectation, LocalDate dateFinAffectation) {
    }

    record AffecterAssureRequest(LocalDate dateDebutAffectation, LocalDate dateFinAffectation) {
    }

    record UpdateAssureRequest(
            String nom, String prenom, String sexe, String dateNaissance,
            String telPersonnel, String telMobile, String telBureau,
            String adresse, String groupeSanguin) {
    }

    @GetMapping
    public ResponseEntity<?> list(@RequestParam UUID centerId,
                                  @RequestParam String userId,
                                  @RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "10") int size,
                                  @RequestParam(required = false) String search,
                                  @RequestParam(required = false) String code,
                                  @RequestParam(required = false) String nom,
                                  @RequestParam(required = false) String prenom,
                                  @RequestParam(required = false) String sexe,
                                  @RequestParam(required = false) String dateAdmission,
                                  @RequestParam(required = false) String numeroAssurance,
                                  @RequestParam(required = false) String etatPatient,
                                  @RequestParam(required = false) String nonFacturable) {
        var center = CenterId.of(centerId);
        var rows = useCase.listPatients(center).stream().map(p -> {
            var pecs = pecUseCase.listByPatient(center, p.getId().value());
            boolean facturable = pecs.stream().anyMatch(pc -> pc.getStatus() == PecStatus.VALIDEE);
            var primaryPec = pecs.stream().filter(pc -> pc.getStatus() == PecStatus.VALIDEE).findFirst().orElse(null);

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.getId().value());
            m.put("codePatient", p.getCodePatient());
            m.put("nom", p.getNom());
            m.put("prenom", p.getPrenom());
            m.put("sexe", p.getSexe());
            m.put("dateAdmission", p.getDateAdmission());
            m.put("numeroAssurance", p.getNumeroAssurance().value());
            m.put("etatPatient", p.getEtatPatient());
            m.put("nonFacturable", !facturable);

            // Additional patient assignment info
            m.put("medecinTraitantId", p.getMedecinTraitantId());
            m.put("positionId", p.getPositionId());
            m.put("transporteurAllerId", p.getTransporteurAllerId());
            m.put("transporteurRetourId", p.getTransporteurRetourId());
            m.put("joursDialyse", p.getJoursDialyse());

            // PEC info
            m.put("pecStatus", primaryPec != null ? primaryPec.getStatus().toString() : "");
            m.put("pecForfaitId", primaryPec != null && primaryPec.getForfaitDemandeId() != null ? primaryPec.getForfaitDemandeId().toString() : "");

            return m;
        }).toList();

        LocalDate dateFilter = parseFlexibleDate(dateAdmission);
        if (dateAdmission != null && !dateAdmission.isBlank() && dateFilter == null) {
            return ResponseEntity.ok(Map.of("items", List.of(), "total", 0, "page", page, "size", size));
        }

        Boolean nonFacturableFilter = null;
        if (nonFacturable != null && !nonFacturable.isBlank()) {
            String normalized = nonFacturable.trim().toLowerCase();
            if ("true".equals(normalized) || "oui".equals(normalized) || "1".equals(normalized)) {
                nonFacturableFilter = true;
            } else if ("false".equals(normalized) || "non".equals(normalized) || "0".equals(normalized)) {
                nonFacturableFilter = false;
            }
        }

        final String searchLc = search == null ? "" : search.toLowerCase();
        final String codeLc = code == null ? "" : code.toLowerCase();
        final String nomLc = nom == null ? "" : nom.toLowerCase();
        final String prenomLc = prenom == null ? "" : prenom.toLowerCase();
        final String sexeLc = sexe == null ? "" : sexe.toLowerCase();
        final String assuranceLc = numeroAssurance == null ? "" : numeroAssurance.toLowerCase();
        final String etatLc = etatPatient == null ? "" : etatPatient.toLowerCase();
        final LocalDate dateFilterFinal = dateFilter;
        final Boolean nonFacturableFilterFinal = nonFacturableFilter;

        var filtered = rows.stream().filter(r -> {
            String rowCode = String.valueOf(r.get("codePatient")).toLowerCase();
            String rowNom = String.valueOf(r.get("nom")).toLowerCase();
            String rowPrenom = String.valueOf(r.get("prenom")).toLowerCase();
            String rowSexe = String.valueOf(r.get("sexe")).toLowerCase();
            String rowAssurance = String.valueOf(r.get("numeroAssurance")).toLowerCase();
            String rowEtat = String.valueOf(r.get("etatPatient")).toLowerCase();

            boolean globalMatch = searchLc.isBlank() || rowCode.contains(searchLc) || rowNom.contains(searchLc)
                    || rowPrenom.contains(searchLc) || rowAssurance.contains(searchLc) || rowEtat.contains(searchLc);
            if (!globalMatch) return false;
            if (!codeLc.isBlank() && !rowCode.contains(codeLc)) return false;
            if (!nomLc.isBlank() && !rowNom.contains(nomLc)) return false;
            if (!prenomLc.isBlank() && !rowPrenom.contains(prenomLc)) return false;
            if (!sexeLc.isBlank() && !rowSexe.contains(sexeLc)) return false;
            if (!assuranceLc.isBlank() && !rowAssurance.contains(assuranceLc)) return false;
            if (!etatLc.isBlank() && !rowEtat.contains(etatLc)) return false;
            if (nonFacturableFilterFinal != null) {
                Object nf = r.get("nonFacturable");
                boolean rowNf = Boolean.TRUE.equals(nf);
                if (!nonFacturableFilterFinal.equals(rowNf)) return false;
            }
            if (dateFilterFinal != null) {
                Object v = r.get("dateAdmission");
                return v instanceof LocalDate && dateFilterFinal.equals(v);
            }
            return true;
        }).toList();

        int safePage = Math.max(page, 0);
        int safeSize = Math.max(size, 1);
        int start = safePage * safeSize;
        int end = Math.min(start + safeSize, filtered.size());
        var items = start >= filtered.size() ? List.of() : filtered.subList(start, end);

        return ResponseEntity.ok(Map.of(
                "items", items,
                "total", filtered.size(),
                "page", safePage,
                "size", safeSize
        ));
    }
}
