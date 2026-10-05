package com.hemodialyse.backend.domain.seance.service;

import com.hemodialyse.backend.domain.article.port.ArticleRepositoryPort;
import com.hemodialyse.backend.domain.patient.port.PatientRepositoryPort;
import com.hemodialyse.backend.domain.patient.vo.PatientId;
import com.hemodialyse.backend.domain.seance.model.Seance;
import com.hemodialyse.backend.domain.seance.model.SeanceArticleConsumption;
import com.hemodialyse.backend.domain.seance.model.SeanceDetails;
import com.hemodialyse.backend.domain.seance.model.SeanceListItem;
import com.hemodialyse.backend.domain.seance.model.SeanceRecap;
import com.hemodialyse.backend.domain.seance.model.SeanceSearch;
import com.hemodialyse.backend.domain.seance.model.SeanceStatus;
import com.hemodialyse.backend.domain.seance.port.SeanceBillingEligibilityPort;
import com.hemodialyse.backend.domain.seance.port.SeanceForfaitCatalogPort;
import com.hemodialyse.backend.domain.seance.port.SeanceRepositoryPort;
import com.hemodialyse.backend.domain.seance.port.SeanceUseCase;
import com.hemodialyse.backend.domain.seance.port.VoletMedicalRepositoryPort;
import com.hemodialyse.backend.domain.seance.port.VoletParamedicalRepositoryPort;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.domain.stock.model.Lot;
import com.hemodialyse.backend.domain.stock.model.SortieRequestItem;
import com.hemodialyse.backend.domain.stock.port.BonSortieUseCase;
import com.hemodialyse.backend.domain.stock.port.LotRepositoryPort;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Domain Service — Seance business rules (aggregate orchestration).
 * <p>
 * Pure domain class (no Spring/JPA dependency — hexagonal architecture, AGENTS.md §3).
 * Because {@code validate} performs several writes (séance state + bon de sortie
 * FEFO), the transactional boundary is provided by
 * {@code application.seance.SeanceApplicationService}, the Spring bean exposed for
 * the {@link SeanceUseCase} port.
 */
public class SeanceDomainService implements SeanceUseCase {

    private final SeanceRepositoryPort seanceRepo;
    private final PatientRepositoryPort patientRepo;
    private final ArticleRepositoryPort articleRepo;
    private final LotRepositoryPort lotRepo;
    private final BonSortieUseCase bonSortieUseCase;
    private final VoletParamedicalRepositoryPort voletParamedicalRepo;
    private final VoletMedicalRepositoryPort voletMedicalRepo;
    private final SeanceForfaitCatalogPort forfaitCatalogPort;
    private final SeanceBillingEligibilityPort billingEligibilityPort;

    public SeanceDomainService(SeanceRepositoryPort seanceRepo,
                               PatientRepositoryPort patientRepo,
                               ArticleRepositoryPort articleRepo,
                               LotRepositoryPort lotRepo,
                               BonSortieUseCase bonSortieUseCase,
                               VoletParamedicalRepositoryPort voletParamedicalRepo,
                               VoletMedicalRepositoryPort voletMedicalRepo,
                               SeanceForfaitCatalogPort forfaitCatalogPort,
                               SeanceBillingEligibilityPort billingEligibilityPort) {
        this.seanceRepo = seanceRepo;
        this.patientRepo = patientRepo;
        this.articleRepo = articleRepo;
        this.lotRepo = lotRepo;
        this.bonSortieUseCase = bonSortieUseCase;
        this.voletParamedicalRepo = voletParamedicalRepo;
        this.voletMedicalRepo = voletMedicalRepo;
        this.forfaitCatalogPort = forfaitCatalogPort;
        this.billingEligibilityPort = billingEligibilityPort;
    }

    @Override
    public Seance create(CenterId centerId, UUID patientId, LocalDate dateSeance) {
        patientRepo.findById(PatientId.of(patientId), centerId)
                .orElseThrow(() -> new IllegalArgumentException("Patient introuvable"));

        LocalDate effectiveDate = dateSeance != null ? dateSeance : LocalDate.now();
        if (!billingEligibilityPort.isPatientBillableAt(centerId, patientId, effectiveDate)) {
            throw new IllegalStateException("Le patient doit avoir une prise en charge valide pour être facturé");
        }

        Seance seance = new Seance(UUID.randomUUID(), patientId, centerId.value(), effectiveDate);
        return seanceRepo.save(seance);
    }

    @Override
    public Seance createFromQr(CenterId centerId, String qrCode) {
        UUID patientId = resolvePatientIdFromQr(centerId, qrCode);
        LocalDate date = LocalDate.now();
        return seanceRepo.findByPatientIdAndDate(centerId, patientId, date)
                .orElseGet(() -> create(centerId, patientId, date));
    }

    @Override
    public ScanResult scanAndValidate(CenterId centerId, String qrCode, String userId) {
        UUID patientId = resolvePatientIdFromQr(centerId, qrCode);
        LocalDate date = LocalDate.now();
        Optional<Seance> existante = seanceRepo.findByPatientIdAndDate(centerId, patientId, date);
        if (existante.isEmpty()) {
            Seance creee = create(centerId, patientId, date);
            return new ScanResult(validate(centerId, creee.getId(), userId, List.of()), true, true, false);
        }
        Seance seance = existante.get();
        return switch (seance.getStatus()) {
            case CREE -> new ScanResult(validate(centerId, seance.getId(), userId, List.of()), false, true, false);
            case VALIDEE, SIGNEE, FACTUREE -> new ScanResult(seance, false, false, true);
            default -> throw new IllegalStateException(
                    "La séance du jour de ce patient est marquée absente : elle ne peut pas être validée");
        };
    }

    @Override
    public void addConsommableSeance(CenterId centerId, UUID seanceId, UUID articleId, BigDecimal quantite, String userId) {
        if (quantite == null || quantite.signum() <= 0) {
            throw new IllegalArgumentException("La quantite doit etre strictement positive");
        }
        Seance seance = seanceRepo.findById(seanceId, centerId)
                .orElseThrow(() -> new IllegalArgumentException("Seance introuvable"));
        if (seance.getStatus() == SeanceStatus.FACTUREE) {
            throw new IllegalStateException("La seance facturee ne peut plus etre modifiee");
        }
        if (seance.getStatus() != SeanceStatus.VALIDEE && seance.getStatus() != SeanceStatus.SIGNEE) {
            throw new IllegalStateException("La seance doit etre validee avant d'ajouter un consommable");
        }
        var article = articleRepo.findById(articleId, centerId)
                .orElseThrow(() -> new IllegalArgumentException("Article introuvable: " + articleId));
        if (!article.isActive()) {
            throw new IllegalStateException("Article inactif: " + article.getCode());
        }
        bonSortieUseCase.addSeanceConsommation(centerId, seanceId, seance.getPatientId(), seance.getDateSeance(),
                articleId, quantite, userId != null ? userId : "system");
    }

    @Override
    public List<SeanceRecap> recentByPatient(CenterId centerId, UUID patientId, LocalDate before, int limit) {
        int bounded = Math.max(1, Math.min(limit, RECENT_MAX));
        LocalDate until = before != null ? before : LocalDate.now();
        return seanceRepo.findRecentByPatient(centerId, patientId, until, bounded).stream()
                .map(s -> new SeanceRecap(s, voletParamedicalRepo.findBySeanceId(s.getId(), centerId).orElse(null)))
                .toList();
    }

    @Override
    public SeanceDetails getDetails(CenterId centerId, UUID seanceId) {
        Seance seance = seanceRepo.findById(seanceId, centerId)
                .orElseThrow(() -> new IllegalArgumentException("Seance introuvable"));
        var patient = patientRepo.findById(PatientId.of(seance.getPatientId()), centerId)
                .orElseThrow(() -> new IllegalArgumentException("Patient introuvable"));
        var voletParamedical = voletParamedicalRepo.findBySeanceId(seance.getId(), centerId).orElse(null);
        var voletMedical = voletMedicalRepo.findBySeanceId(seance.getId(), centerId).orElse(null);
        return new SeanceDetails(seance, patient, voletParamedical, voletMedical);
    }

    @Override
    public PagedResult<SeanceListItem> search(CenterId centerId, SeanceSearch criteria, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, SEARCH_MAX_SIZE));
        SeanceSearch effective = criteria != null ? criteria : SeanceSearch.all();
        if (effective.from() != null && effective.to() != null && effective.from().isAfter(effective.to())) {
            throw new IllegalArgumentException("La date de debut doit preceder la date de fin");
        }
        return seanceRepo.search(centerId, effective, safePage, safeSize);
    }

    @Override
    public Seance updateDate(CenterId centerId, UUID seanceId, LocalDate dateSeance) {
        Seance seance = seanceRepo.findById(seanceId, centerId)
                .orElseThrow(() -> new IllegalArgumentException("Seance introuvable"));
        if (seance.getStatus() == SeanceStatus.FACTUREE) {
            throw new IllegalStateException("La seance facturee ne peut plus etre modifiee");
        }
        seance.setDateSeance(dateSeance != null ? dateSeance : LocalDate.now());
        return seanceRepo.save(seance);
    }

    @Override
    public Seance updateForfait(CenterId centerId, UUID seanceId, UUID forfaitId, String userId) {
        Seance seance = seanceRepo.findById(seanceId, centerId)
                .orElseThrow(() -> new IllegalArgumentException("Seance introuvable"));
        var forfait = forfaitCatalogPort.findById(centerId, forfaitId)
                .orElseThrow(() -> new IllegalArgumentException("Forfait introuvable pour le centre actif"));
        seance.modifierForfait(forfait.id(), forfait.code(), forfait.nom(), forfait.prix(), userId);
        return seanceRepo.save(seance);
    }

    @Override
    public Seance unlockForRegularisation(CenterId centerId, UUID seanceId, String userId) {
        Seance seance = seanceRepo.findById(seanceId, centerId)
                .orElseThrow(() -> new IllegalArgumentException("Seance introuvable"));
        seance.deverrouillerPourRegularisation(userId != null ? userId : "system", LocalDate.now());
        return seanceRepo.save(seance);
    }

    @Override
    public Seance validate(CenterId centerId, UUID seanceId, String userId, List<SeanceArticleConsumption> consommations) {
        Seance seance = seanceRepo.findById(seanceId, centerId)
                .orElseThrow(() -> new IllegalArgumentException("Seance introuvable"));

        seance.validerParInfirmier(userId != null ? userId : "system");

        List<SeanceArticleConsumption> items = consommations != null ? consommations : List.of();

        if (!items.isEmpty()) {
            // Validate articles and build FEFO-based SortieRequestItem list
            List<SortieRequestItem> sortieItems = new ArrayList<>();
            for (SeanceArticleConsumption item : items) {
                if (item == null || item.articleId() == null) {
                    throw new IllegalArgumentException("Article de consommation invalide");
                }

                var article = articleRepo.findById(item.articleId(), centerId)
                        .orElseThrow(() -> new IllegalArgumentException("Article introuvable: " + item.articleId()));

                if (!article.isActive()) {
                    throw new IllegalStateException("Article inactif: " + article.getCode());
                }

                // Auto-select lots using FEFO ordering
                List<Lot> fefoLots = lotRepo.findAvailableByArticleFefo(item.articleId(), centerId);
                BigDecimal remaining = item.quantite();

                for (Lot lot : fefoLots) {
                    if (remaining.signum() <= 0) break;
                    BigDecimal dispo = lot.getQuantiteRestante() != null ? lot.getQuantiteRestante() : BigDecimal.ZERO;
                    if (dispo.signum() <= 0) continue;
                    BigDecimal take = remaining.min(dispo);
                    sortieItems.add(new SortieRequestItem(item.articleId(), lot.getId(), take));
                    remaining = remaining.subtract(take);
                }

                if (remaining.signum() > 0) {
                    throw new IllegalStateException(
                            "Stock insuffisant pour l'article " + article.getCode()
                                    + " (manque: " + remaining + " " + article.getUnite() + ")");
                }
            }

            // Create a BonSortie with PMP valuation via FEFO lot selection
            bonSortieUseCase.create(
                    centerId,
                    seance.getId(),
                    seance.getPatientId(),
                    "SEANCE",
                    seance.getDateSeance(),
                    sortieItems,
                    userId != null ? userId : "system"
            );
        }

        return seanceRepo.save(seance);
    }

    @Override
    public Seance signByMedecin(CenterId centerId, UUID seanceId, String userId) {
        Seance seance = seanceRepo.findById(seanceId, centerId)
                .orElseThrow(() -> new IllegalArgumentException("Seance introuvable"));

        seance.signerParMedecin(userId != null ? userId : "system");
        return seanceRepo.save(seance);
    }

    @Override
    public void removeConsommableSeance(CenterId centerId, UUID seanceId, UUID articleId, String userId) {
        Seance seance = seanceRepo.findById(seanceId, centerId)
                .orElseThrow(() -> new IllegalArgumentException("Seance introuvable"));
        if (seance.getStatus() == SeanceStatus.FACTUREE) {
            throw new IllegalStateException("La seance facturee ne peut plus etre modifiee");
        }
        bonSortieUseCase.setSeanceConsommation(centerId, seanceId, seance.getPatientId(), seance.getDateSeance(),
                articleId, BigDecimal.ZERO, userId);
    }

    @Override
    public void updateConsommableSeance(CenterId centerId, UUID seanceId, UUID articleId,
                                        BigDecimal newQuantite, String userId) {
        if (newQuantite == null || newQuantite.signum() <= 0) {
            throw new IllegalArgumentException("La nouvelle quantite doit etre strictement positive");
        }
        Seance seance = seanceRepo.findById(seanceId, centerId)
                .orElseThrow(() -> new IllegalArgumentException("Seance introuvable"));
        if (seance.getStatus() == SeanceStatus.FACTUREE) {
            throw new IllegalStateException("La seance facturee ne peut plus etre modifiee");
        }
        // Le bon de sortie unique de la séance est mis à jour avec la nouvelle quantité de l'article
        bonSortieUseCase.setSeanceConsommation(centerId, seanceId, seance.getPatientId(),
                seance.getDateSeance(), articleId, newQuantite, userId);
    }

    private UUID resolvePatientIdFromQr(CenterId centerId, String qrCode) {
        if (qrCode == null || qrCode.isBlank()) {
            throw new IllegalArgumentException("QR invalide");
        }
        String raw = qrCode.trim();
        String normalized = normalizeToken(raw);
        int sep = raw.indexOf(':');
        if (sep > 0 && sep < raw.length() - 1) {
            normalized = normalizeToken(raw.substring(sep + 1).trim());
        }

        UUID parsedUuid = tryParseUuid(raw);
        if (parsedUuid == null) {
            parsedUuid = tryParseUuid(normalized);
        }
        if (parsedUuid != null) {
            patientRepo.findById(PatientId.of(parsedUuid), centerId)
                    .orElseThrow(() -> new IllegalArgumentException("Patient introuvable"));
            return parsedUuid;
        }

        var byCode = patientRepo.findByCodePatient(centerId, normalized.toUpperCase());
        if (byCode.isPresent()) {
            return byCode.get().getId().value();
        }

        var byAssurance = patientRepo.findByNumeroAssurance(centerId, normalized);
        if (byAssurance.isPresent()) {
            return byAssurance.get().getId().value();
        }

        // Fallback tolérant: compare sans espaces, tirets ni ponctuation.
        var normalizedQuery = normalizeComparable(raw);
        var patients = patientRepo.findAllByCenter(centerId);
        for (var patient : patients) {
            if (patient == null) {
                continue;
            }
            if (normalizedQuery.equals(normalizeComparable(patient.getCodePatient()))
                    || normalizedQuery.equals(normalizeComparable(patient.getNumeroAssurance() != null ? patient.getNumeroAssurance().value() : null))
                    || normalizedQuery.equals(normalizeComparable(patient.getAssureNumeroAssurance()))) {
                return patient.getId().value();
            }
        }

        throw new IllegalArgumentException("Patient introuvable dans le centre actif à partir du QR: vérifiez le code patient / numéro d'assurance et le centre sélectionné");
    }

    private UUID tryParseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String normalizeToken(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    private String normalizeComparable(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toUpperCase().replaceAll("[^A-Z0-9]", "");
    }
}
