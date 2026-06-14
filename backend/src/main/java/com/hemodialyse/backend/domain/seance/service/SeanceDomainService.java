package com.hemodialyse.backend.domain.seance.service;

import com.hemodialyse.backend.domain.article.port.ArticleRepositoryPort;
import com.hemodialyse.backend.domain.patient.port.PatientRepositoryPort;
import com.hemodialyse.backend.domain.patient.vo.PatientId;
import com.hemodialyse.backend.domain.seance.model.Seance;
import com.hemodialyse.backend.domain.seance.model.SeanceArticleConsumption;
import com.hemodialyse.backend.domain.seance.model.SeanceDetails;
import com.hemodialyse.backend.domain.seance.model.SeanceListItem;
import com.hemodialyse.backend.domain.seance.port.SeanceRepositoryPort;
import com.hemodialyse.backend.domain.seance.port.SeanceUseCase;
import com.hemodialyse.backend.domain.seance.port.VoletMedicalRepositoryPort;
import com.hemodialyse.backend.domain.seance.port.VoletParamedicalRepositoryPort;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.domain.stock.model.StockMovement;
import com.hemodialyse.backend.domain.stock.port.StockMovementRepositoryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class SeanceDomainService implements SeanceUseCase {

    private final SeanceRepositoryPort seanceRepo;
    private final PatientRepositoryPort patientRepo;
    private final ArticleRepositoryPort articleRepo;
    private final StockMovementRepositoryPort stockMovementRepo;
    private final VoletParamedicalRepositoryPort voletParamedicalRepo;
    private final VoletMedicalRepositoryPort voletMedicalRepo;

    public SeanceDomainService(SeanceRepositoryPort seanceRepo,
                               PatientRepositoryPort patientRepo,
                               ArticleRepositoryPort articleRepo,
                               StockMovementRepositoryPort stockMovementRepo,
                               VoletParamedicalRepositoryPort voletParamedicalRepo,
                               VoletMedicalRepositoryPort voletMedicalRepo) {
        this.seanceRepo = seanceRepo;
        this.patientRepo = patientRepo;
        this.articleRepo = articleRepo;
        this.stockMovementRepo = stockMovementRepo;
        this.voletParamedicalRepo = voletParamedicalRepo;
        this.voletMedicalRepo = voletMedicalRepo;
    }

    @Override
    public Seance create(CenterId centerId, UUID patientId, LocalDate dateSeance) {
        patientRepo.findById(PatientId.of(patientId), centerId)
                .orElseThrow(() -> new IllegalArgumentException("Patient introuvable"));

        Seance seance = new Seance(UUID.randomUUID(), patientId, centerId.value(),
                dateSeance != null ? dateSeance : LocalDate.now());
        return seanceRepo.save(seance);
    }

    @Override
    public Seance createFromQr(CenterId centerId, String qrCode, LocalDate dateSeance) {
        UUID patientId = resolvePatientIdFromQr(centerId, qrCode);
        LocalDate date = dateSeance != null ? dateSeance : LocalDate.now();
        return seanceRepo.findByPatientIdAndDate(centerId, patientId, date)
                .orElseGet(() -> create(centerId, patientId, date));
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
    public List<SeanceListItem> list(CenterId centerId) {
        return seanceRepo.findAllByCenter(centerId).stream().map(item -> {
            var patient = patientRepo.findById(PatientId.of(item.patientId()), centerId).orElse(null);
            return new SeanceListItem(
                    item.id(),
                    item.centerId(),
                    item.patientId(),
                    patient != null ? patient.getCodePatient() : null,
                    patient != null ? patient.getNom() : null,
                    patient != null ? patient.getPrenom() : null,
                    item.dateSeance(),
                    item.status(),
                    item.createdAt(),
                    item.validatedAt(),
                    item.signedByInfirmierAt(),
                    item.signedByMedecinAt()
            );
        }).toList();
    }

    @Override
    public Seance updateDate(CenterId centerId, UUID seanceId, LocalDate dateSeance) {
        Seance seance = seanceRepo.findById(seanceId, centerId)
                .orElseThrow(() -> new IllegalArgumentException("Seance introuvable"));
        if (seance.getStatus() == com.hemodialyse.backend.domain.seance.model.SeanceStatus.SIGNEE) {
            throw new IllegalStateException("La séance signée ne peut plus être modifiée");
        }
        seance.setDateSeance(dateSeance != null ? dateSeance : LocalDate.now());
        return seanceRepo.save(seance);
    }

    @Override
    public Seance validate(CenterId centerId, UUID seanceId, String userId, List<SeanceArticleConsumption> consommations) {
        Seance seance = seanceRepo.findById(seanceId, centerId)
                .orElseThrow(() -> new IllegalArgumentException("Seance introuvable"));

        seance.validerParInfirmier(userId != null ? userId : "system");

        List<SeanceArticleConsumption> items = consommations != null ? consommations : List.of();
        for (SeanceArticleConsumption item : items) {
            if (item == null || item.articleId() == null) {
                throw new IllegalArgumentException("Article de consommation invalide");
            }

            var article = articleRepo.findById(item.articleId(), centerId)
                    .orElseThrow(() -> new IllegalArgumentException("Article introuvable: " + item.articleId()));

            if (!article.isActive()) {
                throw new IllegalStateException("Article inactif: " + article.getCode());
            }

            article.debiter(item.quantite());
            articleRepo.save(article);

            stockMovementRepo.save(StockMovement.sortie(
                    centerId.value(),
                    article.getId(),
                    seance.getId(),
                    item.quantite(),
                    userId != null ? userId : "system"
            ));
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



