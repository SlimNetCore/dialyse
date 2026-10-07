package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.application.notification.NotificationService;
import com.hemodialyse.backend.domain.article.model.Article;
import com.hemodialyse.backend.domain.article.port.ArticleRepositoryPort;
import com.hemodialyse.backend.domain.seance.model.PrescriptionMedicale;
import com.hemodialyse.backend.domain.seance.port.PrescriptionMedicaleUseCase;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.infrastructure.security.CenterAccessGuard;
import com.hemodialyse.backend.infrastructure.web.dto.request.PrescriptionMedicaleSearchRequest;
import com.hemodialyse.backend.infrastructure.web.dto.request.UpsertPrescriptionMedicaleRequest;
import com.hemodialyse.backend.infrastructure.web.dto.response.EntityWriteResponse;
import com.hemodialyse.backend.infrastructure.web.dto.response.PagedResponse;
import com.hemodialyse.backend.infrastructure.web.dto.response.PrescriptionMedicaleResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Prescriptions médicales du patient : cibles de dialyse et traitement de l'anémie (EPO, fer injectable).
 * <p>
 * Accès réservé au corps médical : le MEDECIN écrit, l'ADMIN consulte. L'INFIRMIER consulte
 * uniquement la prescription en vigueur ({@code /prescriptions/active}) — c'est elle qu'il doit
 * suivre pour administrer l'EPO/le fer pendant la séance.
 */
@RestController
@RequestMapping("/api/v1/patients")
public class PrescriptionMedicaleRestController {

    private final PrescriptionMedicaleUseCase useCase;
    private final CenterAccessGuard centerAccessGuard;
    private final ArticleRepositoryPort articleRepository;
    private final NotificationService notifications;

    public PrescriptionMedicaleRestController(PrescriptionMedicaleUseCase useCase,
                                              CenterAccessGuard centerAccessGuard,
                                              ArticleRepositoryPort articleRepository,
                                              NotificationService notifications) {
        this.useCase = useCase;
        this.centerAccessGuard = centerAccessGuard;
        this.articleRepository = articleRepository;
        this.notifications = notifications;
    }

    private PrescriptionMedicaleResponse toResponse(PrescriptionMedicale p, CenterId center) {
        String epoCode = null;
        String epoLibelle = null;
        if (p.getEpoArticleId() != null) {
            Article article = articleRepository.findById(p.getEpoArticleId(), center).orElse(null);
            if (article != null) {
                epoCode = article.getCode();
                epoLibelle = article.getLibelle();
            }
        }
        String ferCode = null;
        String ferLibelle = null;
        if (p.getFerArticleId() != null) {
            Article article = articleRepository.findById(p.getFerArticleId(), center).orElse(null);
            if (article != null) {
                ferCode = article.getCode();
                ferLibelle = article.getLibelle();
            }
        }
        return PrescriptionMedicaleResponse.from(p, epoCode, epoLibelle, ferCode, ferLibelle);
    }

    @PreAuthorize("hasAnyRole('MEDECIN','ADMIN')")
    @GetMapping("/{patientId}/prescriptions")
    public ResponseEntity<PagedResponse<PrescriptionMedicaleResponse>> list(
            @PathVariable UUID patientId,
            @RequestParam(required = false) UUID centerId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        CenterId center = centerAccessGuard.requireCenter(centerId);
        var paged = useCase.listPagedByPatient(center, patientId, from, to, page, size);
        return ResponseEntity.ok(PagedResponse.from(paged, p -> toResponse(p, center)));
    }

    /**
     * Prescription en vigueur à une date donnée (par défaut aujourd'hui) — la plus récente dont
     * la date de prescription est antérieure ou égale à {@code date}. Utilisé par l'infirmier
     * pendant la séance pour savoir quoi administrer.
     */
    @PreAuthorize("hasAnyRole('MEDECIN','ADMIN','INFIRMIER')")
    @GetMapping("/{patientId}/prescriptions/active")
    public ResponseEntity<PrescriptionMedicaleResponse> active(
            @PathVariable UUID patientId,
            @RequestParam(required = false) UUID centerId,
            @RequestParam(required = false) LocalDate date) {
        CenterId center = centerAccessGuard.requireCenter(centerId);
        LocalDate at = date != null ? date : LocalDate.now();
        var prescriptions = useCase.listByPatient(center, patientId, null, at);
        return prescriptions.isEmpty()
                ? ResponseEntity.noContent().build()
                : ResponseEntity.ok(toResponse(prescriptions.get(0), center));
    }

    @PreAuthorize("hasAnyRole('MEDECIN','ADMIN')")
    @PostMapping("/prescriptions/search")
    public ResponseEntity<PagedResponse<PrescriptionMedicaleResponse>> searchPrescriptions(
            @RequestBody @Valid PrescriptionMedicaleSearchRequest criteria) {
        CenterId center = centerAccessGuard.requireCenter(criteria.centerId());
        var paged = useCase.listPagedByPatient(
                center,
                criteria.patientId(),
                criteria.dateFrom(),
                criteria.dateTo(),
                criteria.page(),
                criteria.size());
        return ResponseEntity.ok(PagedResponse.from(paged, p -> toResponse(p, center)));
    }

    @PreAuthorize("hasRole('MEDECIN')")
    @PostMapping("/{patientId}/prescriptions")
    public ResponseEntity<EntityWriteResponse> create(@PathVariable UUID patientId,
                                                      @RequestBody @Valid UpsertPrescriptionMedicaleRequest request) {
        return ResponseEntity.ok(save(patientId, null, request));
    }

    @PreAuthorize("hasRole('MEDECIN')")
    @PutMapping("/{patientId}/prescriptions/{prescriptionId}")
    public ResponseEntity<EntityWriteResponse> update(@PathVariable UUID patientId,
                                                      @PathVariable UUID prescriptionId,
                                                      @RequestBody @Valid UpsertPrescriptionMedicaleRequest request) {
        return ResponseEntity.ok(save(patientId, prescriptionId, request));
    }

    @PreAuthorize("hasRole('MEDECIN')")
    @DeleteMapping("/{patientId}/prescriptions/{prescriptionId}")
    public ResponseEntity<Void> delete(@PathVariable UUID patientId,
                                       @PathVariable UUID prescriptionId,
                                       @RequestParam(required = false) UUID centerId) {
        CenterId center = centerAccessGuard.requireCenter(centerId);
        useCase.delete(center, patientId, prescriptionId);
        notifications.notifyPrescriptionChanged(center.value(), patientId);
        return ResponseEntity.noContent().build();
    }

    /**
     * La dose prescrite (UI, mg) doit pouvoir se convertir dans l'unité de stock de l'article choisi, sinon
     * l'administration ne saurait pas quelle quantité sortir : le médecin est averti dès la prescription.
     */
    private void verifierConversionDose(CenterId center, UUID articleId, Integer dose, String uniteDose) {
        if (articleId == null || dose == null || dose <= 0) {
            return;
        }
        articleRepository.findById(articleId, center)
                .ifPresent(article -> article.quantiteStockPourDose(BigDecimal.valueOf(dose), uniteDose));
    }

    private EntityWriteResponse save(UUID patientId, UUID prescriptionId, UpsertPrescriptionMedicaleRequest request) {
        CenterId center = centerAccessGuard.requireCenter(request.centerId());
        verifierConversionDose(center, request.epoArticleId(), request.epoDoseUi(), "UI");
        verifierConversionDose(center, request.ferArticleId(), request.ferDoseMg(), "mg");
        var prescription = useCase.save(
                center,
                patientId,
                prescriptionId,
                request.datePrescription(),
                request.medecinId(),
                request.qbCible(),
                request.qdCible(),
                request.ufMaxMl(),
                request.dureeCibleMin(),
                request.poidsSecCibleKg(),
                request.typeDialyseurPrescrit(),
                request.anticoagTypePrescrit(),
                request.epoArticleId(),
                request.epoDoseUi(),
                request.epoVoie(),
                request.epoFrequenceValeur(),
                request.epoFrequenceUnite(),
                request.ferArticleId(),
                request.ferDoseMg(),
                request.ferVoie(),
                request.ferFrequenceValeur(),
                request.ferFrequenceUnite()
        );
        notifications.notifyPrescriptionChanged(center.value(), patientId);
        return new EntityWriteResponse(prescription.getId(), prescription.getPatientId(), prescription.getUpdatedAt());
    }
}
