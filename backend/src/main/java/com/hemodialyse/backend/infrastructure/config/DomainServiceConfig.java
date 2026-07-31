package com.hemodialyse.backend.infrastructure.config;

import com.hemodialyse.backend.domain.article.port.ArticleRepositoryPort;
import com.hemodialyse.backend.domain.insurance.port.AttestationRepositoryPort;
import com.hemodialyse.backend.domain.insurance.service.AttestationDomainService;
import com.hemodialyse.backend.domain.patient.port.PatientRepositoryPort;
import com.hemodialyse.backend.domain.pec.port.PecRepositoryPort;
import com.hemodialyse.backend.domain.pec.service.PecDomainService;
import com.hemodialyse.backend.domain.shared.port.TransactionRunner;
import com.hemodialyse.backend.domain.referential.port.ReferentialRepositoryPort;
import com.hemodialyse.backend.domain.referential.service.ReferentialDomainService;
import com.hemodialyse.backend.domain.seance.port.AbordVasculaireRepositoryPort;
import com.hemodialyse.backend.domain.seance.port.DossierMedicalPatientRepositoryPort;
import com.hemodialyse.backend.domain.seance.port.PrescriptionMedicaleRepositoryPort;
import com.hemodialyse.backend.domain.seance.port.ResultatAnalyseRepositoryPort;
import com.hemodialyse.backend.domain.seance.port.SeanceRepositoryPort;
import com.hemodialyse.backend.domain.seance.port.VoletMedicalRepositoryPort;
import com.hemodialyse.backend.domain.seance.port.VoletParamedicalRepositoryPort;
import com.hemodialyse.backend.domain.seance.service.AbordVasculaireDomainService;
import com.hemodialyse.backend.domain.seance.service.DossierMedicalPatientDomainService;
import com.hemodialyse.backend.domain.seance.service.PrescriptionMedicaleDomainService;
import com.hemodialyse.backend.domain.seance.service.ResultatAnalyseDomainService;
import com.hemodialyse.backend.domain.seance.service.VoletMedicalDomainService;
import com.hemodialyse.backend.domain.seance.service.VoletParamedicalDomainService;
import com.hemodialyse.backend.domain.stock.port.BonCommandeRepositoryPort;
import com.hemodialyse.backend.domain.stock.port.BonReceptionRepositoryPort;
import com.hemodialyse.backend.domain.stock.port.BonSortieRepositoryPort;
import com.hemodialyse.backend.domain.stock.port.EmplacementRepositoryPort;
import com.hemodialyse.backend.domain.stock.port.FournisseurRepositoryPort;
import com.hemodialyse.backend.domain.stock.port.LotRepositoryPort;
import com.hemodialyse.backend.domain.stock.port.StockDashboardPort;
import com.hemodialyse.backend.domain.stock.port.StockEventPublisher;
import com.hemodialyse.backend.domain.stock.port.StockMovementRepositoryPort;
import com.hemodialyse.backend.domain.stock.port.StockSequencePort;
import com.hemodialyse.backend.domain.stock.service.BonCommandeService;
import com.hemodialyse.backend.domain.stock.service.PmpEngine;
import com.hemodialyse.backend.domain.stock.service.PmpRecalculationCoordinator;
import com.hemodialyse.backend.domain.stock.service.StockDashboardService;
import com.hemodialyse.backend.domain.stock.service.StockReferentialDomainService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires pure domain services (no Spring/JPA dependency) as Spring beans.
 * <p>
 * Keeping the {@code @Service} / {@code @Transactional} annotations out of the
 * {@code domain/} package enforces the hexagonal boundary (AGENTS.md §3 &amp; §4):
 * high-level use cases stay framework-agnostic and are assembled here, in the
 * infrastructure layer. Transaction atomicity is provided by the persistence
 * adapters (Spring Data repositories are transactional by default).
 */
@Configuration
public class DomainServiceConfig {

    @Bean
    public PecDomainService pecDomainService(PecRepositoryPort pecRepo,
                                             PatientRepositoryPort patientRepo,
                                             AttestationRepositoryPort attestationRepo) {
        return new PecDomainService(pecRepo, patientRepo, attestationRepo);
    }

    @Bean
    public AttestationDomainService attestationDomainService(AttestationRepositoryPort attestationRepo) {
        return new AttestationDomainService(attestationRepo);
    }

    @Bean
    public ReferentialDomainService referentialDomainService(ReferentialRepositoryPort repo) {
        return new ReferentialDomainService(repo);
    }

    @Bean
    public VoletMedicalDomainService voletMedicalDomainService(SeanceRepositoryPort seanceRepo,
                                                               VoletMedicalRepositoryPort voletRepo) {
        return new VoletMedicalDomainService(seanceRepo, voletRepo);
    }

    @Bean
    public VoletParamedicalDomainService voletParamedicalDomainService(SeanceRepositoryPort seanceRepo,
                                                                       VoletParamedicalRepositoryPort voletRepo) {
        return new VoletParamedicalDomainService(seanceRepo, voletRepo);
    }

    @Bean
    public AbordVasculaireDomainService abordVasculaireDomainService(AbordVasculaireRepositoryPort repo) {
        return new AbordVasculaireDomainService(repo);
    }

    @Bean
    public ResultatAnalyseDomainService resultatAnalyseDomainService(ResultatAnalyseRepositoryPort repo) {
        return new ResultatAnalyseDomainService(repo);
    }

    @Bean
    public PrescriptionMedicaleDomainService prescriptionMedicaleDomainService(PrescriptionMedicaleRepositoryPort repo) {
        return new PrescriptionMedicaleDomainService(repo);
    }

    @Bean
    public DossierMedicalPatientDomainService dossierMedicalPatientDomainService(DossierMedicalPatientRepositoryPort repo) {
        return new DossierMedicalPatientDomainService(repo);
    }

    @Bean
    public PmpEngine pmpEngine(StockMovementRepositoryPort movementRepo, ArticleRepositoryPort articleRepo) {
        return new PmpEngine(movementRepo, articleRepo);
    }

    @Bean
    public PmpRecalculationCoordinator pmpRecalculationCoordinator(PmpEngine pmpEngine,
                                                                   StockEventPublisher events,
                                                                   TransactionRunner transactionRunner) {
        return new PmpRecalculationCoordinator(pmpEngine, events, transactionRunner);
    }

    @Bean
    public BonCommandeService bonCommandeService(BonCommandeRepositoryPort repo,
                                                 StockSequencePort sequence) {
        return new BonCommandeService(repo, sequence);
    }

    @Bean
    public StockReferentialDomainService stockReferentialDomainService(FournisseurRepositoryPort fournisseurRepo,
                                                                       EmplacementRepositoryPort emplacementRepo,
                                                                       ArticleRepositoryPort articleRepo) {
        return new StockReferentialDomainService(fournisseurRepo, emplacementRepo, articleRepo);
    }

    @Bean
    public StockDashboardService stockDashboardService(StockDashboardPort dashboardPort,
                                                       StockMovementRepositoryPort movementRepo,
                                                       ArticleRepositoryPort articleRepo,
                                                       LotRepositoryPort lotRepo,
                                                       BonReceptionRepositoryPort bonReceptionRepo,
                                                       BonSortieRepositoryPort bonSortieRepo) {
        return new StockDashboardService(dashboardPort, movementRepo, articleRepo, lotRepo, bonReceptionRepo, bonSortieRepo);
    }
}








