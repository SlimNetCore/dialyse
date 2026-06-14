package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.application.notification.NotificationService;
import com.hemodialyse.backend.domain.seance.model.SeanceArticleConsumption;
import com.hemodialyse.backend.domain.seance.port.SeanceUseCase;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.infrastructure.web.dto.request.*;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.sql.Date;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/seances")
public class SeanceRestController {

    private final SeanceUseCase seanceUseCase;
    private final NotificationService notificationService;
    private final JdbcTemplate jdbc;

    public SeanceRestController(SeanceUseCase seanceUseCase,
                                NotificationService notificationService,
                                JdbcTemplate jdbc) {
        this.seanceUseCase = seanceUseCase;
        this.notificationService = notificationService;
        this.jdbc = jdbc;
    }

    @PreAuthorize("hasAnyRole('ADMIN','INFIRMIER')")
    @PostMapping
    public ResponseEntity<?> create(@RequestBody @Valid CreateSeanceRequest request) {
        var seance = seanceUseCase.create(CenterId.of(request.centerId()), request.patientId(), request.dateSeance());

        var details = seanceUseCase.getDetails(CenterId.of(request.centerId()), seance.getId());
        var patient = details.patient();
        notificationService.notifySeanceCreated(
                request.centerId(),
                seance.getId(),
                seance.getPatientId(),
                patient.getNom(),
                patient.getPrenom(),
                seance.getDateSeance() == null ? null : seance.getDateSeance().toString()
        );

        return ResponseEntity.ok(Map.of(
                "id", seance.getId(),
                "status", seance.getStatus(),
                "dateSeance", seance.getDateSeance()
        ));
    }

    @PreAuthorize("hasAnyRole('ADMIN','INFIRMIER','MEDECIN','SECRETAIRE')")
    @GetMapping
    public ResponseEntity<?> list(@RequestParam UUID centerId) {
        return ResponseEntity.ok(seanceUseCase.list(CenterId.of(centerId)));
    }

    @PreAuthorize("hasAnyRole('ADMIN','INFIRMIER','SECRETAIRE')")
    @PostMapping("/scan")
    public ResponseEntity<?> scanQr(@RequestBody @Valid ScanSeanceQrRequest request) {
        var seance = seanceUseCase.createFromQr(CenterId.of(request.centerId()), request.qrCode(), request.dateSeance());

        var details = seanceUseCase.getDetails(CenterId.of(request.centerId()), seance.getId());
        var patient = details.patient();
        notificationService.notifySeanceCreated(
                request.centerId(),
                seance.getId(),
                seance.getPatientId(),
                patient.getNom(),
                patient.getPrenom(),
                seance.getDateSeance() == null ? null : seance.getDateSeance().toString()
        );

        return ResponseEntity.ok(Map.of(
                "id", seance.getId(),
                "status", seance.getStatus(),
                "dateSeance", seance.getDateSeance()
        ));
    }

    @PreAuthorize("hasAnyRole('ADMIN','INFIRMIER','MEDECIN','SECRETAIRE')")
    @GetMapping("/{seanceId}")
    public ResponseEntity<?> details(@PathVariable UUID seanceId,
                                     @RequestParam UUID centerId) {
        var details = seanceUseCase.getDetails(CenterId.of(centerId), seanceId);
        var seance = details.seance();
        var patient = details.patient();
        var voletParamedical = details.voletParamedical();
        var voletMedical = details.voletMedical();

        var seanceMap = new LinkedHashMap<String, Object>();
        seanceMap.put("id", seance.getId());
        seanceMap.put("centerId", seance.getCenterId());
        seanceMap.put("patientId", seance.getPatientId());
        seanceMap.put("dateSeance", seance.getDateSeance());
        seanceMap.put("status", seance.getStatus());
        seanceMap.put("createdAt", seance.getCreatedAt());
        seanceMap.put("validatedAt", seance.getValidatedAt());
        seanceMap.put("signedByInfirmierAt", seance.getSignedByInfirmierAt());
        seanceMap.put("signedByMedecinAt", seance.getSignedByMedecinAt());

        var patientMap = new LinkedHashMap<String, Object>();
        patientMap.put("id", patient.getId().value());
        patientMap.put("codePatient", patient.getCodePatient());
        patientMap.put("nom", patient.getNom());
        patientMap.put("prenom", patient.getPrenom());
        patientMap.put("sexe", patient.getSexe());
        patientMap.put("dateNaissance", patient.getDateNaissance());
        patientMap.put("numeroAssurance", patient.getNumeroAssurance() == null ? null : patient.getNumeroAssurance().value());
        patientMap.put("groupeSanguin", patient.getGroupeSanguin());
        patientMap.put("telMobile", patient.getTelMobile());
        patientMap.put("medecinTraitantId", patient.getMedecinTraitantId());
        patientMap.put("salleId", patient.getSalleId());
        patientMap.put("positionId", patient.getPositionId());

        Map<String, Object> paramedicalMap = null;
        if (voletParamedical != null) {
            paramedicalMap = new LinkedHashMap<>();
            paramedicalMap.put("poidsAvantKg", voletParamedical.getPoidsAvantKg());
            paramedicalMap.put("poidsApresKg", voletParamedical.getPoidsApresKg());
            paramedicalMap.put("taAvant", voletParamedical.getTaAvant());
            paramedicalMap.put("taApres", voletParamedical.getTaApres());
            paramedicalMap.put("dureeMinutes", voletParamedical.getDureeMinutes());
            paramedicalMap.put("debitSangMlMin", voletParamedical.getDebitSangMlMin());
            paramedicalMap.put("ultrafiltrationMl", voletParamedical.getUltrafiltrationMl());
            paramedicalMap.put("anticoagulant", voletParamedical.getAnticoagulant());
            paramedicalMap.put("typeDialysat", voletParamedical.getTypeDialysat());
            paramedicalMap.put("incidents", voletParamedical.getIncidents());
        }

        Map<String, Object> medicalMap = null;
        if (voletMedical != null) {
            medicalMap = new LinkedHashMap<>();
            medicalMap.put("prescription", voletMedical.getPrescription());
            medicalMap.put("toleranceSeance", voletMedical.getToleranceSeance());
            medicalMap.put("examenClinique", voletMedical.getExamenClinique());
            medicalMap.put("resultatsBiologiques", voletMedical.getResultatsBiologiques());
            medicalMap.put("ajustementsTherapeutiques", voletMedical.getAjustementsTherapeutiques());
            medicalMap.put("conclusionMedicale", voletMedical.getConclusionMedicale());
        }

        var payload = new LinkedHashMap<String, Object>();
        payload.put("seance", seanceMap);
        payload.put("patient", patientMap);
        payload.put("paramedical", paramedicalMap);
        payload.put("medical", medicalMap);
        return ResponseEntity.ok(payload);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{seanceId}")
    public ResponseEntity<?> update(@PathVariable UUID seanceId, @RequestBody @Valid UpdateSeanceRequest request) {
        var seance = seanceUseCase.updateDate(CenterId.of(request.centerId()), seanceId, request.dateSeance());
        return ResponseEntity.ok(Map.of(
                "id", seance.getId(),
                "status", seance.getStatus(),
                "dateSeance", seance.getDateSeance()
        ));
    }

    @PreAuthorize("hasAnyRole('ADMIN','INFIRMIER')")
    @PostMapping("/{seanceId}/valider")
    public ResponseEntity<?> validate(@PathVariable UUID seanceId, @RequestBody @Valid ValidateSeanceRequest request) {
        var consommations = request.consommations() == null
                ? java.util.List.<SeanceArticleConsumption>of()
                : request.consommations().stream()
                .map(item -> new SeanceArticleConsumption(item.articleId(), item.quantite()))
                .toList();

        var seance = seanceUseCase.validate(CenterId.of(request.centerId()), seanceId, request.userId(), consommations);
        var details = seanceUseCase.getDetails(CenterId.of(request.centerId()), seance.getId());
        var patient = details.patient();
        notificationService.notifySeanceValidated(
                request.centerId(),
                seance.getId(),
                seance.getPatientId(),
                patient.getNom(),
                patient.getPrenom(),
                seance.getDateSeance() == null ? null : seance.getDateSeance().toString()
        );

        return ResponseEntity.ok(Map.of(
                "id", seance.getId(),
                "status", seance.getStatus(),
                "validatedAt", seance.getValidatedAt(),
                "signedByInfirmierAt", seance.getSignedByInfirmierAt()
        ));
    }

    @PreAuthorize("hasAnyRole('ADMIN','INFIRMIER','MEDECIN','SECRETAIRE')")
    @GetMapping("/journal")
    public ResponseEntity<?> journalByDate(@RequestParam UUID centerId,
                                           @RequestParam LocalDate dateSeance) {
        var patients = jdbc.query(
                """
                        SELECT s.id AS seance_id,
                               s.patient_id AS patient_id,
                               s.date_seance AS date_seance,
                               s.statut AS statut,
                               p.code_patient AS code_patient,
                               p.nom AS nom,
                               p.prenom AS prenom
                        FROM seances s
                        INNER JOIN patients p ON p.id = s.patient_id
                        WHERE s.center_id = ?
                          AND s.date_seance = ?
                        ORDER BY p.nom ASC, p.prenom ASC
                        """,
                (rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("seanceId", rs.getObject("seance_id", UUID.class));
                    row.put("patientId", rs.getObject("patient_id", UUID.class));
                    row.put("dateSeance", rs.getObject("date_seance", Date.class).toLocalDate());
                    row.put("status", rs.getString("statut"));
                    row.put("patientCode", rs.getString("code_patient"));
                    row.put("patientNom", rs.getString("nom"));
                    row.put("patientPrenom", rs.getString("prenom"));
                    return row;
                },
                centerId,
                Date.valueOf(dateSeance)
        );

        var articles = jdbc.query(
                """
                        SELECT sm.article_id AS article_id,
                               a.code AS article_code,
                               a.libelle AS article_libelle,
                               SUM(sm.quantite) AS quantite_totale
                        FROM stock_movements sm
                        INNER JOIN seances s ON s.id = sm.seance_id AND s.center_id = sm.center_id
                        INNER JOIN articles a ON a.id = sm.article_id AND a.center_id = sm.center_id
                        WHERE sm.center_id = ?
                          AND sm.mouvement_type = 'SORTIE'
                          AND s.date_seance = ?
                        GROUP BY sm.article_id, a.code, a.libelle
                        ORDER BY a.code ASC
                        """,
                (rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("articleId", rs.getObject("article_id", UUID.class));
                    row.put("articleCode", rs.getString("article_code"));
                    row.put("articleLibelle", rs.getString("article_libelle"));
                    row.put("quantiteTotale", rs.getBigDecimal("quantite_totale"));
                    return row;
                },
                centerId,
                Date.valueOf(dateSeance)
        );

        var payload = new LinkedHashMap<String, Object>();
        payload.put("dateSeance", dateSeance);
        payload.put("patients", patients);
        payload.put("sortiesArticles", articles);
        return ResponseEntity.ok(payload);
    }

    @PreAuthorize("hasAnyRole('ADMIN','MEDECIN')")
    @PostMapping("/{seanceId}/signer-medecin")
    public ResponseEntity<?> signByMedecin(@PathVariable UUID seanceId,
                                           @RequestBody @Valid SignSeanceMedecinRequest request) {
        var seance = seanceUseCase.signByMedecin(CenterId.of(request.centerId()), seanceId, request.userId());
        return ResponseEntity.ok(Map.of(
                "id", seance.getId(),
                "status", seance.getStatus(),
                "signedByMedecinAt", seance.getSignedByMedecinAt()
        ));
    }
}



