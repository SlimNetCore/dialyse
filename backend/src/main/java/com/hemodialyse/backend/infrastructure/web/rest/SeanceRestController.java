package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.domain.seance.model.SeanceArticleConsumption;
import com.hemodialyse.backend.domain.seance.port.SeanceUseCase;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.infrastructure.web.dto.request.*;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/seances")
public class SeanceRestController {

    private final SeanceUseCase seanceUseCase;

    public SeanceRestController(SeanceUseCase seanceUseCase) {
        this.seanceUseCase = seanceUseCase;
    }

    @PreAuthorize("hasAnyRole('ADMIN','INFIRMIER')")
    @PostMapping
    public ResponseEntity<?> create(@RequestBody @Valid CreateSeanceRequest request) {
        var seance = seanceUseCase.create(CenterId.of(request.centerId()), request.patientId(), request.dateSeance());
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
        return ResponseEntity.ok(Map.of(
                "id", seance.getId(),
                "status", seance.getStatus(),
                "validatedAt", seance.getValidatedAt(),
                "signedByInfirmierAt", seance.getSignedByInfirmierAt()
        ));
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



