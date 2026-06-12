package com.hemodialyse.backend.domain.seance.service;

import com.hemodialyse.backend.domain.article.port.ArticleRepositoryPort;
import com.hemodialyse.backend.domain.patient.port.PatientRepositoryPort;
import com.hemodialyse.backend.domain.patient.vo.PatientId;
import com.hemodialyse.backend.domain.seance.model.Seance;
import com.hemodialyse.backend.domain.seance.model.SeanceArticleConsumption;
import com.hemodialyse.backend.domain.seance.port.SeanceRepositoryPort;
import com.hemodialyse.backend.domain.seance.port.SeanceUseCase;
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

    public SeanceDomainService(SeanceRepositoryPort seanceRepo,
                               PatientRepositoryPort patientRepo,
                               ArticleRepositoryPort articleRepo,
                               StockMovementRepositoryPort stockMovementRepo) {
        this.seanceRepo = seanceRepo;
        this.patientRepo = patientRepo;
        this.articleRepo = articleRepo;
        this.stockMovementRepo = stockMovementRepo;
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
}



