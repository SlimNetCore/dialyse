package com.hemodialyse.backend.domain.medical.greffe.service;

import com.hemodialyse.backend.domain.medical.greffe.aggregate.EtapeBilanPreGreffe;
import com.hemodialyse.backend.domain.medical.greffe.port.EtapeBilanPreGreffeRepositoryPort;
import com.hemodialyse.backend.domain.medical.greffe.valueobject.CategorieEtapeGreffe;
import com.hemodialyse.backend.domain.medical.greffe.valueobject.StatutEtapeGreffe;
import com.hemodialyse.backend.domain.shared.exception.BusinessException;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EtapeBilanPreGreffeDomainServiceTest {

    private final CenterId centerId = CenterId.of(UUID.randomUUID());
    private final UUID patientId = UUID.randomUUID();
    private FakeRepository repository;
    private EtapeBilanPreGreffeDomainService service;

    @BeforeEach
    void setUp() {
        repository = new FakeRepository();
        service = new EtapeBilanPreGreffeDomainService(repository);
    }

    @Test
    void genererEtapesStandard_should_create_all_template_steps_once() {
        List<EtapeBilanPreGreffe> first = service.genererEtapesStandard(centerId, patientId);

        assertThat(first).hasSize(EtapeBilanPreGreffeTemplate.ETAPES_STANDARD.size());
    }

    @Test
    void genererEtapesStandard_should_be_idempotent() {
        service.genererEtapesStandard(centerId, patientId);
        List<EtapeBilanPreGreffe> second = service.genererEtapesStandard(centerId, patientId);

        assertThat(second).hasSize(EtapeBilanPreGreffeTemplate.ETAPES_STANDARD.size());
    }

    @Test
    void update_should_reject_fait_status_without_date_realisation() {
        EtapeBilanPreGreffe etape = service.create(centerId, patientId, CategorieEtapeGreffe.CARDIOLOGIQUE, "ECG");

        assertThatThrownBy(() -> service.update(centerId, patientId, etape.getId(), StatutEtapeGreffe.FAIT,
                null, "normal", null, null, null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void update_should_accept_fait_status_with_date_realisation() {
        EtapeBilanPreGreffe etape = service.create(centerId, patientId, CategorieEtapeGreffe.CARDIOLOGIQUE, "ECG");

        EtapeBilanPreGreffe updated = service.update(centerId, patientId, etape.getId(), StatutEtapeGreffe.FAIT,
                LocalDate.now(), "normal", null, null, null);

        assertThat(updated.getStatut()).isEqualTo(StatutEtapeGreffe.FAIT);
        assertThat(updated.getResultat()).isEqualTo("normal");
    }

    private static final class FakeRepository implements EtapeBilanPreGreffeRepositoryPort {
        private final List<EtapeBilanPreGreffe> saved = new ArrayList<>();

        @Override
        public List<EtapeBilanPreGreffe> findByPatientId(UUID patientId, CenterId centerId) {
            return List.copyOf(saved);
        }

        @Override
        public Optional<EtapeBilanPreGreffe> findById(UUID id, UUID patientId, CenterId centerId) {
            return saved.stream().filter(e -> e.getId().equals(id)).findFirst();
        }

        @Override
        public EtapeBilanPreGreffe save(EtapeBilanPreGreffe etape) {
            saved.removeIf(e -> e.getId().equals(etape.getId()));
            saved.add(etape);
            return etape;
        }

        @Override
        public void deleteById(UUID id, UUID patientId, CenterId centerId) {
            saved.removeIf(e -> e.getId().equals(id));
        }
    }
}
