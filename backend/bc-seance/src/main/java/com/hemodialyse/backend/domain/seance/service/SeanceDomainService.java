package com.hemodialyse.backend.domain.seance.service;

import com.hemodialyse.backend.domain.shared.exception.SeanceFactureeNonModifiableException;
import com.hemodialyse.backend.domain.patient.port.PatientRepositoryPort;
import com.hemodialyse.backend.domain.patient.vo.PatientId;
import com.hemodialyse.backend.domain.seance.model.DerogationPlanning;
import com.hemodialyse.backend.domain.seance.model.Seance;
import com.hemodialyse.backend.domain.seance.model.SeanceArticleConsumption;
import com.hemodialyse.backend.domain.seance.model.SeanceDetails;
import com.hemodialyse.backend.domain.seance.model.SeanceListItem;
import com.hemodialyse.backend.domain.seance.model.SeanceSearch;
import com.hemodialyse.backend.domain.seance.model.SeanceStatus;
import com.hemodialyse.backend.domain.seance.port.SeanceBillingEligibilityPort;
import com.hemodialyse.backend.domain.seance.port.SeancePlacePort;
import com.hemodialyse.backend.domain.seance.port.SeancePlanningPort;
import com.hemodialyse.backend.domain.seance.port.SeanceRepositoryPort;
import com.hemodialyse.backend.domain.seance.port.SeanceUseCase;
import com.hemodialyse.backend.domain.seance.port.VoletMedicalRepositoryPort;
import com.hemodialyse.backend.domain.seance.port.VoletParamedicalRepositoryPort;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.domain.shared.exception.BusinessException;
import com.hemodialyse.backend.domain.shared.vo.CenterId;

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
    private final VoletParamedicalRepositoryPort voletParamedicalRepo;
    private final VoletMedicalRepositoryPort voletMedicalRepo;
    private final SeanceBillingEligibilityPort billingEligibilityPort;
    private final SeancePlanningPort planningPort;
    private final SeancePlacePort placePort;

    public SeanceDomainService(SeanceRepositoryPort seanceRepo,
                               PatientRepositoryPort patientRepo,
                               VoletParamedicalRepositoryPort voletParamedicalRepo,
                               VoletMedicalRepositoryPort voletMedicalRepo,
                               SeanceBillingEligibilityPort billingEligibilityPort,
                               SeancePlanningPort planningPort,
                               SeancePlacePort placePort) {
        this.seanceRepo = seanceRepo;
        this.patientRepo = patientRepo;
        this.voletParamedicalRepo = voletParamedicalRepo;
        this.voletMedicalRepo = voletMedicalRepo;
        this.billingEligibilityPort = billingEligibilityPort;
        this.planningPort = planningPort;
        this.placePort = placePort;
    }

    private static DerogationPlanning exigerDerogation(DerogationPlanning derogation, String code, String message) {
        if (derogation == null) {
            throw new BusinessException(code, message);
        }
        return derogation;
    }

    @Override
    public Seance create(CenterId centerId, UUID patientId, LocalDate dateSeance) {
        return creer(centerId, patientId, dateSeance, null);
    }

    private Seance creer(CenterId centerId, UUID patientId, LocalDate dateSeance, DerogationPlanning derogation) {
        patientRepo.findById(PatientId.of(patientId), centerId)
                .orElseThrow(() -> new IllegalArgumentException("Patient introuvable"));

        LocalDate effectiveDate = dateSeance != null ? dateSeance : LocalDate.now();
        billingEligibilityPort.causeNonFacturable(centerId, patientId, effectiveDate).ifPresent(cause -> {
            throw new IllegalStateException(switch (cause) {
                case PRISE_EN_CHARGE -> "Le patient doit avoir une prise en charge valide pour être facturé";
                case PRISE_EN_CHARGE_NON_VALIDEE -> "La prise en charge du patient couvre le " + effectiveDate
                        + " mais n'est pas validée : validez-la pour pouvoir le facturer";
                case ATTESTATION_DROITS -> "La prise en charge du patient est valide mais aucune attestation de droits "
                        + "ne couvre le " + effectiveDate + " : renouvelez l'attestation pour pouvoir le facturer";
            });
        });

        Seance seance = new Seance(UUID.randomUUID(), patientId, centerId.value(), effectiveDate);
        if (derogation != null) {
            seance.marquerHorsPlanning(derogation);
        }
        return seanceRepo.save(seance);
    }

    /**
     * Le patient doit être programmé à la date de la séance (jour de dialyse, centre ouvert, patient présent).
     * Sinon : un jour de fermeture n'est franchi que par un administrateur confirmant un motif ; tout autre hors
     * planning demande une confirmation avec motif (jamais accordée à la secrétaire, qui ne confirme pas).
     *
     * @return la dérogation à enregistrer sur la séance, ou {@code null} si le patient est attendu
     */
    private DerogationPlanning verifierProgramme(CenterId centerId, UUID patientId, LocalDate date,
                                                 DerogationPlanning derogation) {
        var raison = ProgrammationSeance.evaluer(planningPort.situation(centerId, patientId, date), date);
        if (raison.isEmpty()) {
            return null;
        }
        return switch (raison.get()) {
            case CENTRE_FERME -> {
                if (derogation == null || !derogation.administrateur()) {
                    throw new BusinessException("SEANCE_CENTRE_FERME",
                            "Le centre est fermé ce jour : seul un administrateur peut enregistrer une séance");
                }
                yield derogation;
            }
            case JOUR_NON_DIALYSE -> exigerDerogation(derogation, "SEANCE_HORS_PLANNING_JOUR",
                    "Ce patient n'est pas programmé aujourd'hui (ce n'est pas un de ses jours de dialyse)");
            case PATIENT_NON_ATTENDU -> exigerDerogation(derogation, "SEANCE_HORS_PLANNING_PATIENT",
                    "Ce patient n'est pas attendu aujourd'hui (sommeil, sortie ou séjour terminé)");
        };
    }

    @Override
    public Seance createFromQr(CenterId centerId, String qrCode) {
        UUID patientId = resolvePatientIdFromQr(centerId, qrCode);
        LocalDate date = LocalDate.now();
        return seanceRepo.findByPatientIdAndDate(centerId, patientId, date)
                .orElseGet(() -> {
                    verifierProgramme(centerId, patientId, date, null);
                    return create(centerId, patientId, date);
                });
    }

    @Override
    public ScanResult scanAndValidate(CenterId centerId, String qrCode, String userId, DerogationPlanning derogation) {
        UUID patientId = resolvePatientIdFromQr(centerId, qrCode);
        LocalDate date = LocalDate.now();
        Optional<Seance> existante = seanceRepo.findByPatientIdAndDate(centerId, patientId, date);
        if (existante.isEmpty()) {
            DerogationPlanning retenue = verifierProgramme(centerId, patientId, date, derogation);
            Seance creee = creer(centerId, patientId, date, retenue);
            return new ScanResult(validerParLeScanResiduel(centerId, creee.getId(), userId), true, true, false);
        }
        Seance seance = existante.get();
        return switch (seance.getStatus()) {
            case CREE -> new ScanResult(validerParLeScanResiduel(centerId, seance.getId(), userId), false, true, false);
            case VALIDEE, SIGNEE, FACTUREE -> new ScanResult(seance, false, false, true);
            default -> throw new IllegalStateException(
                    "La séance du jour de ce patient est marquée absente : elle ne peut pas être validée");
        };
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

    /**
     * RÉSIDU (SE2, ADR 0089, revue au plus tard le 2026-12-15) : la validation sans consommable que {@code scanAndValidate} (lui-même
     * résidu de SE1, sans appelant de production) employait via {@code validate}, retirée avec la reprise de SE2. Retiré avec lui.
     */
    private Seance validerParLeScanResiduel(CenterId centerId, UUID seanceId, String userId) {
        Seance seance = seanceRepo.findById(seanceId, centerId)
                .orElseThrow(() -> new IllegalArgumentException("Seance introuvable"));
        seance.validerParInfirmier(userId != null ? userId : "system");
        if (seance.getPlace() == null) {
            placePort.placeLe(centerId, seance.getPatientId(), seance.getDateSeance()).ifPresent(seance::memoriserPlace);
        }
        return seanceRepo.save(seance);
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
