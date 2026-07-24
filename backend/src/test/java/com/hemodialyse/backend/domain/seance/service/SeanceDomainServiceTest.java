package com.hemodialyse.backend.domain.seance.service;
import com.hemodialyse.backend.domain.article.model.Article;
import com.hemodialyse.backend.domain.article.port.ArticleRepositoryPort;
import com.hemodialyse.backend.domain.patient.model.Patient;
import com.hemodialyse.backend.domain.patient.port.PatientRepositoryPort;
import com.hemodialyse.backend.domain.patient.vo.PatientId;
import com.hemodialyse.backend.domain.seance.model.Seance;
import com.hemodialyse.backend.domain.seance.model.SeanceArticleConsumption;
import com.hemodialyse.backend.domain.seance.model.SeanceListItem;
import com.hemodialyse.backend.domain.seance.model.SeanceStatus;
import com.hemodialyse.backend.domain.seance.port.SeanceRepositoryPort;
import com.hemodialyse.backend.domain.seance.port.VoletMedicalRepositoryPort;
import com.hemodialyse.backend.domain.seance.port.VoletParamedicalRepositoryPort;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.domain.stock.model.StockMovement;
import com.hemodialyse.backend.domain.stock.port.StockMovementRepositoryPort;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
class SeanceDomainServiceTest {
    private static SeanceDomainService buildService(
            SeanceRepositoryPort seanceRepo,
            PatientRepositoryPort patientRepo,
            ArticleRepositoryPort articleRepo,
            StockMovementRepositoryPort movementRepo) {
        return new SeanceDomainService(seanceRepo, patientRepo, articleRepo, movementRepo,
                mock(VoletParamedicalRepositoryPort.class), mock(VoletMedicalRepositoryPort.class));
    }
    @Test
    void validate_should_debit_stock_and_create_movement() {
        CenterId centerId = CenterId.of(UUID.randomUUID());
        UUID patientId = UUID.randomUUID();
        UUID seanceId = UUID.randomUUID();
        UUID articleId = UUID.randomUUID();
        InMemorySeanceRepository seanceRepo = new InMemorySeanceRepository();
        InMemoryPatientRepository patientRepo = new InMemoryPatientRepository(patientId, centerId);
        InMemoryArticleRepository articleRepo = new InMemoryArticleRepository(articleId, centerId.value(), new BigDecimal("10"));
        InMemoryStockMovementRepository movementRepo = new InMemoryStockMovementRepository();
        Seance seance = new Seance(seanceId, patientId, centerId.value(), LocalDate.now());
        seanceRepo.save(seance);
        SeanceDomainService service = buildService(seanceRepo, patientRepo, articleRepo, movementRepo);
        Seance validated = service.validate(centerId, seanceId, "inf-01", List.of(
                new SeanceArticleConsumption(articleId, new BigDecimal("2"))
        ));
        assertEquals(SeanceStatus.VALIDEE, validated.getStatus());
        assertEquals(new BigDecimal("8"), articleRepo.lastSaved.getStockQuantity());
        assertEquals(1, movementRepo.movements.size());
        assertEquals(new BigDecimal("2"), movementRepo.movements.getFirst().getQuantite());
    }
    @Test
    void validate_should_fail_when_stock_is_insufficient() {
        CenterId centerId = CenterId.of(UUID.randomUUID());
        UUID patientId = UUID.randomUUID();
        UUID seanceId = UUID.randomUUID();
        UUID articleId = UUID.randomUUID();
        InMemorySeanceRepository seanceRepo = new InMemorySeanceRepository();
        InMemoryPatientRepository patientRepo = new InMemoryPatientRepository(patientId, centerId);
        InMemoryArticleRepository articleRepo = new InMemoryArticleRepository(articleId, centerId.value(), new BigDecimal("1"));
        InMemoryStockMovementRepository movementRepo = new InMemoryStockMovementRepository();
        seanceRepo.save(new Seance(seanceId, patientId, centerId.value(), LocalDate.now()));
        SeanceDomainService service = buildService(seanceRepo, patientRepo, articleRepo, movementRepo);
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.validate(
                centerId,
                seanceId,
                "inf-01",
                List.of(new SeanceArticleConsumption(articleId, new BigDecimal("3")))
        ));
        assertTrue(ex.getMessage().contains("Stock insuffisant"));
        assertEquals(0, movementRepo.movements.size());
    }
    @Test
    void signByMedecin_should_succeed_after_infirmier_validation() {
        CenterId centerId = CenterId.of(UUID.randomUUID());
        UUID patientId = UUID.randomUUID();
        UUID seanceId = UUID.randomUUID();
        InMemorySeanceRepository seanceRepo = new InMemorySeanceRepository();
        InMemoryPatientRepository patientRepo = new InMemoryPatientRepository(patientId, centerId);
        InMemoryArticleRepository articleRepo = new InMemoryArticleRepository(UUID.randomUUID(), centerId.value(), new BigDecimal("10"));
        InMemoryStockMovementRepository movementRepo = new InMemoryStockMovementRepository();
        Seance seance = new Seance(seanceId, patientId, centerId.value(), LocalDate.now());
        seance.validerParInfirmier("inf-01");
        seanceRepo.save(seance);
        SeanceDomainService service = buildService(seanceRepo, patientRepo, articleRepo, movementRepo);
        Seance signed = service.signByMedecin(centerId, seanceId, "med-01");
        assertEquals(SeanceStatus.SIGNEE, signed.getStatus());
        assertNotNull(signed.getSignedByMedecinAt());
        assertEquals("med-01", signed.getSignedByMedecinUserId());
    }
    @Test
    void signByMedecin_should_fail_when_seance_not_validated() {
        CenterId centerId = CenterId.of(UUID.randomUUID());
        UUID patientId = UUID.randomUUID();
        UUID seanceId = UUID.randomUUID();
        InMemorySeanceRepository seanceRepo = new InMemorySeanceRepository();
        InMemoryPatientRepository patientRepo = new InMemoryPatientRepository(patientId, centerId);
        InMemoryArticleRepository articleRepo = new InMemoryArticleRepository(UUID.randomUUID(), centerId.value(), new BigDecimal("10"));
        InMemoryStockMovementRepository movementRepo = new InMemoryStockMovementRepository();
        seanceRepo.save(new Seance(seanceId, patientId, centerId.value(), LocalDate.now()));
        SeanceDomainService service = buildService(seanceRepo, patientRepo, articleRepo, movementRepo);
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.signByMedecin(centerId, seanceId, "med-01"));
        assertTrue(ex.getMessage().contains("validation infirmiere"));
    }
    private static final class InMemorySeanceRepository implements SeanceRepositoryPort {
        private final Map<UUID, Seance> data = new HashMap<>();
        @Override
        public Seance save(Seance seance) {
            data.put(seance.getId(), seance);
            return seance;
        }
        @Override
        public Optional<Seance> findById(UUID seanceId, CenterId centerId) {
            return Optional.ofNullable(data.get(seanceId)).filter(s -> s.getCenterId().equals(centerId.value()));
        }

        @Override
        public Optional<Seance> findByPatientIdAndDate(CenterId centerId, UUID patientId, LocalDate dateSeance) {
            return data.values().stream()
                    .filter(s -> s.getCenterId().equals(centerId.value())
                            && s.getPatientId().equals(patientId)
                            && s.getDateSeance().equals(dateSeance))
                    .findFirst();
        }

        @Override
        public List<SeanceListItem> findAllByCenter(CenterId centerId) {
            return List.of();
        }
    }
    private record InMemoryPatientRepository(UUID patientId, CenterId centerId) implements PatientRepositoryPort {
        @Override
        public Patient save(Patient patient) {
            return patient;
        }

        @Override
        public Optional<Patient> findById(PatientId id, CenterId centerId) {
            return id.value().equals(patientId) && this.centerId.equals(centerId) ? Optional.of(new Patient()) : Optional.empty();
        }

        @Override
        public Optional<Patient> findByCodePatient(CenterId centerId, String codePatient) {
            return Optional.empty();
        }

        @Override
        public Optional<Patient> findByNumeroAssurance(CenterId centerId, String numeroAssurance) {
            return Optional.empty();
        }

        @Override
        public List<Patient> findAllByCenter(CenterId centerId) {
            return List.of();
        }

        @Override
        public long countByCenter(CenterId centerId) {
            return 0;
        }
    }
    private static final class InMemoryArticleRepository implements ArticleRepositoryPort {
        private final Map<UUID, Article> data = new HashMap<>();
        private Article lastSaved;
        private InMemoryArticleRepository(UUID articleId, UUID centerId, BigDecimal stock) {
            Article article = new Article();
            article.setId(articleId);
            article.setCenterId(centerId);
            article.setCode("ART-01");
            article.setLibelle("Article test");
            article.setUnite("piece");
            article.setStockQuantity(stock);
            article.setSeuilAlerte(BigDecimal.ZERO);
            article.setActive(true);
            data.put(articleId, article);
            lastSaved = article;
        }
        @Override
        public Optional<Article> findById(UUID articleId, CenterId centerId) {
            return Optional.ofNullable(data.get(articleId)).filter(a -> a.getCenterId().equals(centerId.value()));
        }
        @Override
        public Article save(Article article) {
            data.put(article.getId(), article);
            lastSaved = article;
            return article;
        }
    }
    private static final class InMemoryStockMovementRepository implements StockMovementRepositoryPort {
        private final LinkedList<StockMovement> movements = new LinkedList<>();
        @Override
        public StockMovement save(StockMovement movement) {
            movements.add(movement);
            return movement;
        }
        @Override
        public List<StockMovement> findByArticleOrdered(CenterId centerId, UUID articleId) {
            return movements.stream()
                    .filter(m -> m.getArticleId().equals(articleId))
                    .toList();
        }
        @Override
        public List<StockMovement> findByArticleBefore(CenterId centerId, UUID articleId, java.time.OffsetDateTime before) {
            return movements.stream()
                    .filter(m -> m.getArticleId().equals(articleId))
                    .filter(m -> m.getCreatedAt() != null && m.getCreatedAt().isBefore(before))
                    .toList();
        }
        @Override
        public void updatePmpApres(UUID movementId, BigDecimal pmpApres) {
            movements.stream()
                    .filter(m -> m.getId() != null && m.getId().equals(movementId))
                    .findFirst()
                    .ifPresent(m -> m.setPmpApres(pmpApres));
        }
        @Override
        public void applyRecalc(List<MovementRecalc> updates) {
            if (updates == null) return;
            for (MovementRecalc u : updates) {
                movements.stream()
                        .filter(m -> m.getId() != null && m.getId().equals(u.movementId()))
                        .findFirst()
                        .ifPresent(m -> {
                            m.setPmpApres(u.pmpApres());
                            if (u.valorisation() != null) m.setPrixUnitaire(u.valorisation());
                        });
            }
        }
        @Override
        public java.util.Optional<StockMovement> findFirstEntreeByLot(CenterId centerId, UUID lotId) {
            return movements.stream()
                    .filter(m -> m.getLotId() != null && m.getLotId().equals(lotId))
                    .filter(m -> m.getMovementType() == com.hemodialyse.backend.domain.stock.model.StockMovementType.ENTREE)
                    .findFirst();
        }
    }
}
