package com.hemodialyse.backend.domain.medical.serologie.service;

import com.hemodialyse.backend.domain.medical.serologie.aggregate.Serologie;
import com.hemodialyse.backend.domain.medical.serologie.port.SerologieRepositoryPort;
import com.hemodialyse.backend.domain.medical.serologie.valueobject.MarqueurSerologique;
import com.hemodialyse.backend.domain.medical.serologie.valueobject.ResultatSerologique;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.domain.shared.exception.BusinessException;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SerologieDomainServiceTest {

    private final CenterId centerId = CenterId.of(UUID.randomUUID());
    private final UUID patientId = UUID.randomUUID();
    private FakeRepository repository;
    private SerologieDomainService service;

    @BeforeEach
    void setUp() {
        repository = new FakeRepository();
        service = new SerologieDomainService(repository);
    }

    @Test
    void create_should_reject_positive_result_without_conduite_a_tenir() {
        assertThatThrownBy(() -> service.create(centerId, patientId, MarqueurSerologique.AG_HBS,
                ResultatSerologique.POSITIF, null, null, LocalDate.now(), "Labo central", null, null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void create_should_reject_duplicate_marker_and_date() {
        service.create(centerId, patientId, MarqueurSerologique.AC_HBS, ResultatSerologique.NEGATIF,
                null, null, LocalDate.of(2026, 1, 1), "Labo central", null, null);

        assertThatThrownBy(() -> service.create(centerId, patientId, MarqueurSerologique.AC_HBS,
                ResultatSerologique.NEGATIF, null, null, LocalDate.of(2026, 1, 1), "Labo central", null, null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void listDerniersResultatsByPatient_should_keep_only_most_recent_per_marker() {
        service.create(centerId, patientId, MarqueurSerologique.AC_HBS, ResultatSerologique.NEGATIF,
                null, null, LocalDate.of(2025, 1, 1), "Labo A", null, null);
        Serologie recent = service.create(centerId, patientId, MarqueurSerologique.AC_HBS, ResultatSerologique.NEGATIF,
                null, null, LocalDate.of(2026, 1, 1), "Labo B", null, null);

        List<Serologie> derniers = service.listDerniersResultatsByPatient(centerId, patientId);

        assertThat(derniers).hasSize(1);
        assertThat(derniers.get(0).getId()).isEqualTo(recent.getId());
    }

    @Test
    void update_should_require_conduite_a_tenir_when_switching_to_positive() {
        Serologie serologie = service.create(centerId, patientId, MarqueurSerologique.VIH_AC,
                ResultatSerologique.EN_COURS, null, null, LocalDate.now(), "Labo central", null, null);

        assertThatThrownBy(() -> service.update(centerId, patientId, serologie.getId(),
                ResultatSerologique.POSITIF, null))
                .isInstanceOf(BusinessException.class);
    }

    private static final class FakeRepository implements SerologieRepositoryPort {
        private final List<Serologie> saved = new ArrayList<>();

        @Override
        public List<Serologie> findByPatientId(UUID patientId, CenterId centerId) {
            return List.copyOf(saved);
        }

        @Override
        public PagedResult<Serologie> findPagedByPatientId(UUID patientId, CenterId centerId, int page, int size) {
            return PagedResult.of(List.copyOf(saved), saved.size(), page, size);
        }

        @Override
        public Optional<Serologie> findById(UUID id, UUID patientId, CenterId centerId) {
            return saved.stream().filter(s -> s.getId().equals(id)).findFirst();
        }

        @Override
        public boolean existsByPatientIdAndMarqueurAndDatePrelevement(UUID patientId, CenterId centerId,
                                                                      MarqueurSerologique marqueur, LocalDate datePrelevement) {
            return saved.stream().anyMatch(s -> s.getMarqueur() == marqueur
                    && s.getDatePrelevement().equals(datePrelevement));
        }

        @Override
        public Serologie save(Serologie serologie) {
            saved.removeIf(s -> s.getId().equals(serologie.getId()));
            saved.add(serologie);
            return serologie;
        }

        @Override
        public void deleteById(UUID id, UUID patientId, CenterId centerId) {
            saved.removeIf(s -> s.getId().equals(id));
        }
    }
}
