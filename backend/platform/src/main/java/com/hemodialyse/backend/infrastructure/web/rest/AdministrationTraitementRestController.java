package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.application.notification.SaisieInfirmierNotifier;
import com.hemodialyse.backend.contexts.stock.publishedlanguage.CatalogueDArticles;
import com.hemodialyse.backend.contexts.stock.publishedlanguage.CatalogueDArticles.RefusDuCatalogue;
import com.hemodialyse.backend.domain.medical.anemie.port.AdministrationTraitementUseCase;
import com.hemodialyse.backend.domain.medical.anemie.valueobject.DoseAdministree;
import com.hemodialyse.backend.domain.medical.anemie.valueobject.TypeTraitementAnemie;
import com.hemodialyse.backend.domain.shared.exception.BusinessException;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.contexts.stock.publishedlanguage.ConsommationsDeSeance;
import com.hemodialyse.backend.infrastructure.security.CenterAccessGuard;
import com.hemodialyse.backend.infrastructure.web.dto.request.CreateAdministrationTraitementRequest;
import com.hemodialyse.backend.infrastructure.web.dto.response.AdministrationTraitementResponse;
import com.hemodialyse.backend.infrastructure.web.dto.response.PagedResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Administrations réelles du traitement de l'anémie (EPO, fer injectable) — distinctes de la
 * prescription : ce qui a effectivement été donné au patient, pas seulement ce qui était prévu.
 * <p>
 * L'administration est la responsabilité de l'INFIRMIER, effectuée pendant la séance en suivant
 * la prescription du MEDECIN en vigueur à sa date (voir {@code /prescriptions/active}). Le
 * MEDECIN garde la capacité d'enregistrer une administration lui-même (cas exceptionnel) et
 * consulte l'historique complet ; l'ADMIN consulte seulement.
 */
@RestController
@RequestMapping("/api/v1/patients")
public class AdministrationTraitementRestController {

    private final AdministrationTraitementUseCase useCase;
    private final CenterAccessGuard centerAccessGuard;
    private final ConsommationsDeSeance consommations;
    private final SaisieInfirmierNotifier saisieNotifier;
    /**
     * Catalogue publié du Stock (slice S1, ADR 0006) : conversion de dose RG-STK-006.
     */
    private final CatalogueDArticles catalogueDArticles;

    public AdministrationTraitementRestController(AdministrationTraitementUseCase useCase,
                                                  CenterAccessGuard centerAccessGuard,
                                                  ConsommationsDeSeance consommations,
                                                  SaisieInfirmierNotifier saisieNotifier,
                                                  CatalogueDArticles catalogueDArticles) {
        this.useCase = useCase;
        this.centerAccessGuard = centerAccessGuard;
        this.consommations = consommations;
        this.saisieNotifier = saisieNotifier;
        this.catalogueDArticles = catalogueDArticles;
    }

    @PreAuthorize("hasAnyRole('MEDECIN','ADMIN','INFIRMIER')")
    @GetMapping("/{patientId}/administrations-anemie")
    public ResponseEntity<PagedResponse<AdministrationTraitementResponse>> list(
            @PathVariable UUID patientId,
            @RequestParam(required = false) UUID centerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        CenterId center = centerAccessGuard.requireCenter(centerId);
        var paged = useCase.listPagedByPatient(center, patientId, page, size);
        return ResponseEntity.ok(PagedResponse.from(paged, AdministrationTraitementResponse::from));
    }

    @PreAuthorize("hasAnyRole('INFIRMIER','MEDECIN')")
    @PostMapping("/{patientId}/administrations-anemie")
    public ResponseEntity<AdministrationTraitementResponse> create(
            @PathVariable UUID patientId, @RequestBody @Valid CreateAdministrationTraitementRequest request) {
        CenterId center = centerAccessGuard.requireCenter(request.centerId());

        // La dose est prescrite en UI / mg ; le stock sort dans l'unité de l'article : la quantité est déduite de la
        // fiche article (dose ÷ dosage par unité) et jamais saisie à la main, pour que stock et prescription concordent.
        BigDecimal quantiteArticle = request.quantiteArticle();
        if (request.administree() && request.articleId() != null && request.dose() != null) {
            try {
                quantiteArticle = catalogueDArticles.quantitePourDose(center.value(), request.articleId(), request.dose(),
                        request.uniteDose());
            } catch (RefusDuCatalogue refus) {
                // même contrat HTTP que le legacy : 422 avec le code stable du Stock
                throw new BusinessException(refus.code(), refus.getMessage());
            }
        }

        boolean sortieStockRequise = request.administree()
                && request.articleId() != null
                && request.seanceId() != null
                && quantiteArticle != null;
        if (sortieStockRequise) {
            // Lève IllegalStateException (-> 422, cf. ApiExceptionHandler) si le stock est insuffisant ;
            // dans ce cas l'administration n'est pas créée, pour éviter une trace sans sortie de stock réelle.
            // Sortie datée du jour réel de l'administration (pas de la date de la séance, qui peut être
            // planifiée dans le futur) : le stock quitte physiquement le magasin au moment du clic, et
            // createViaFefo() génère un bon de sortie numéroté propre à l'administration, distinct du bon
            // « SEANCE » unique qui regroupe les consommables de la séance.
            consommations.administrer(center.value(), request.seanceId(), patientId, LocalDate.now(), request.articleId(),
                    quantiteArticle, request.administrePar());
        }

        DoseAdministree dose = request.dose() == null ? null : new DoseAdministree(request.dose(), request.uniteDose());
        var administration = useCase.create(center, patientId, request.prescriptionMedicaleId(),
                TypeTraitementAnemie.valueOf(request.typeTraitement()), request.molecule(), dose, request.voie(),
                request.dateAdministration(), request.seanceId(), request.administrePar(), request.administree(),
                request.motifNonAdministration(), request.articleId(), quantiteArticle);
        saisieNotifier.saisie(center.value(), "ANEMIE", patientId, request.dateAdministration());
        return ResponseEntity.ok(AdministrationTraitementResponse.from(administration));
    }
}
