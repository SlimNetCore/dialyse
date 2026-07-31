package com.hemodialyse.backend.application.seance;

import com.hemodialyse.backend.domain.article.port.ArticleRepositoryPort;
import com.hemodialyse.backend.domain.patient.port.PatientRepositoryPort;
import com.hemodialyse.backend.domain.seance.model.Seance;
import com.hemodialyse.backend.domain.seance.model.SeanceArticleConsumption;
import com.hemodialyse.backend.domain.seance.model.SeanceDetails;
import com.hemodialyse.backend.domain.seance.model.SeanceListItem;
import com.hemodialyse.backend.domain.seance.port.SeanceRepositoryPort;
import com.hemodialyse.backend.domain.seance.port.SeanceUseCase;
import com.hemodialyse.backend.domain.seance.port.VoletMedicalRepositoryPort;
import com.hemodialyse.backend.domain.seance.port.VoletParamedicalRepositoryPort;
import com.hemodialyse.backend.domain.seance.service.SeanceDomainService;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.domain.stock.port.BonSortieUseCase;
import com.hemodialyse.backend.domain.stock.port.LotRepositoryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Application Service — transactional boundary for the Seance use cases.
 * <p>
 * The pure {@link SeanceDomainService} holds the business rules (no Spring/JPA,
 * hexagonal architecture — AGENTS.md §3). This facade owns the {@code @Transactional}
 * boundary, guaranteeing atomicity of the validation flow (séance state transition
 * + FEFO bon de sortie). It is the single Spring bean exposed for the
 * {@link SeanceUseCase} port.
 */
@Service
@Transactional
public class SeanceApplicationService implements SeanceUseCase {

    private final SeanceDomainService delegate;

    public SeanceApplicationService(SeanceRepositoryPort seanceRepo,
                                    PatientRepositoryPort patientRepo,
                                    ArticleRepositoryPort articleRepo,
                                    LotRepositoryPort lotRepo,
                                    BonSortieUseCase bonSortieUseCase,
                                    VoletParamedicalRepositoryPort voletParamedicalRepo,
                                    VoletMedicalRepositoryPort voletMedicalRepo) {
        this.delegate = new SeanceDomainService(seanceRepo, patientRepo, articleRepo, lotRepo,
                bonSortieUseCase, voletParamedicalRepo, voletMedicalRepo);
    }

    @Override
    public Seance create(CenterId centerId, UUID patientId, LocalDate dateSeance) {
        return delegate.create(centerId, patientId, dateSeance);
    }

    @Override
    public Seance createFromQr(CenterId centerId, String qrCode) {
        return delegate.createFromQr(centerId, qrCode);
    }

    @Override
    @Transactional(readOnly = true)
    public SeanceDetails getDetails(CenterId centerId, UUID seanceId) {
        return delegate.getDetails(centerId, seanceId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SeanceListItem> list(CenterId centerId) {
        return delegate.list(centerId);
    }

    @Override
    public Seance updateDate(CenterId centerId, UUID seanceId, LocalDate dateSeance) {
        return delegate.updateDate(centerId, seanceId, dateSeance);
    }

    @Override
    public Seance validate(CenterId centerId, UUID seanceId, String userId, List<SeanceArticleConsumption> consommations) {
        return delegate.validate(centerId, seanceId, userId, consommations);
    }

    @Override
    public Seance signByMedecin(CenterId centerId, UUID seanceId, String userId) {
        return delegate.signByMedecin(centerId, seanceId, userId);
    }
}

