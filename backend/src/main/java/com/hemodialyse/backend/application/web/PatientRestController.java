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

    @GetMapping("/{id}/assures/history")
    public ResponseEntity<?> assureHistory(@PathVariable UUID id, @RequestParam UUID centerId) {
        var rows = assurePatientRepo.findHistory(CenterId.of(centerId), id).stream().map(h -> {
            var a = assureRepo.findByNumeroAssurance(h.getNumeroAssurance()).orElse(null);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("patientId", id);
            m.put("numeroAssurance", h.getNumeroAssurance());
            m.put("isPrimary", h.isPrimary());
            m.put("dateAffectation", h.getDateAffectation());
            m.put("nom", a != null ? a.getNom() : null);
            m.put("prenom", a != null ? a.getPrenom() : null);
            return m;
        }).toList();
        return ResponseEntity.ok(rows);
    }

    @PostMapping("/{id}/assures/{numeroAssurance}/affecter")
    public ResponseEntity<?> affecterAssure(@PathVariable UUID id, @PathVariable String numeroAssurance, @RequestParam UUID centerId) {
        Patient p = useCase.getPatient(CenterId.of(centerId), id);
        if ("ASSURE_LUI_MEME".equals(p.getQualiteAssure())) {
            throw new IllegalArgumentException("Impossible d'affecter un assuré si le patient est assuré lui-même");
        }
        Assure a = assureRepo.findByNumeroAssurance(numeroAssurance)
            .orElseThrow(() -> new IllegalArgumentException("Assuré introuvable"));
        assurePatientRepo.clearPrimary(CenterId.of(centerId), id);
        AssurePatientAssignment ap = new AssurePatientAssignment();
        ap.setPatientId(id);
        ap.setNumeroAssurance(numeroAssurance);
        ap.setCenterId(centerId);
        ap.setPrimary(true);
        ap.setDateAffectation(java.time.OffsetDateTime.now());
        assurePatientRepo.save(ap);
        return ResponseEntity.ok(Map.of(
            "assigned", true,
            "numeroAssurance", numeroAssurance,
            "nom", Optional.ofNullable(a.getNom()).orElse(""),
            "prenom", Optional.ofNullable(a.getPrenom()).orElse("")
        ));
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
