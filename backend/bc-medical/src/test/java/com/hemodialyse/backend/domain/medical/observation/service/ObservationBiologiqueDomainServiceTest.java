package com.hemodialyse.backend.domain.medical.observation.service;

import com.hemodialyse.backend.domain.medical.observation.aggregate.ObservationBiologique;
import com.hemodialyse.backend.domain.medical.observation.port.ObservationBiologiqueRepositoryPort;
import com.hemodialyse.backend.domain.medical.observation.valueobject.SourceObservation;
import com.hemodialyse.backend.domain.medical.observation.valueobject.ValeurMesuree;
import com.hemodialyse.backend.domain.medical.shared.valueobject.CodingSystem;
import com.hemodialyse.backend.domain.medical.shared.valueobject.ConceptCode;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.domain.shared.exception.BusinessException;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ObservationBiologiqueDomainServiceTest {

    private final CenterId centerId = CenterId.of(UUID.randomUUID());
    private final UUID patientId = UUID.randomUUID();
    private final ConceptCode hemoglobine = ConceptCode.of(CodingSystem.LOINC, "718-7", "Hémoglobine");
    private FakeRepository repository;
    private ObservationBiologiqueDomainService service;

    @BeforeEach
    void setUp() {
        repository = new FakeRepository();
        service = new ObservationBiologiqueDomainService(repository);
    }

    @Test
    void create_should_reject_non_loinc_analyte() {
        ConceptCode local = ConceptCode.of(CodingSystem.LOCAL, "TRUC", "Truc");

        assertThatThrownBy(() -> service.create(centerId, patientId, null, local,
                new ValeurMesuree(BigDecimal.TEN, "g/dL"), null, LocalDate.now(), null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void create_should_reject_both_numeric_and_text_value() {
        assertThatThrownBy(() -> service.create(centerId, patientId, null, hemoglobine,
                new ValeurMesuree(BigDecimal.TEN, "g/dL"), "dix", LocalDate.now(), null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void create_should_persist_numeric_observation() {
        ObservationBiologique observation = service.create(centerId, patientId, null, hemoglobine,
                new ValeurMesuree(new BigDecimal("11.2"), "g/dL"), null, LocalDate.now(), null);

        assertThat(observation.getValeurNum()).isPresent();
        assertThat(observation.getSource()).isEqualTo(SourceObservation.SAISIE_DIRECTE);
    }

    @Test
    void corriger_should_reject_derived_observation() {
        ObservationBiologique derivee = ObservationBiologique.reconstituer(UUID.randomUUID(), patientId,
                centerId.value(), null, hemoglobine, new ValeurMesuree(BigDecimal.TEN, "g/dL"), null,
                LocalDate.now(), com.hemodialyse.backend.domain.medical.observation.valueobject.StatutObservation.FINAL,
                SourceObservation.DERIVEE_BILAN, OffsetDateTime.now(), OffsetDateTime.now());
        repository.save(derivee);

        assertThatThrownBy(() -> service.corriger(centerId, patientId, derivee.getId(),
                new ValeurMesuree(new BigDecimal("12"), "g/dL"), null))
                .isInstanceOf(BusinessException.class);
    }

    private static final class FakeRepository implements ObservationBiologiqueRepositoryPort {
        private final List<ObservationBiologique> saved = new ArrayList<>();

        @Override
        public PagedResult<ObservationBiologique> findPagedByPatientId(UUID patientId, CenterId centerId,
                                                                       String loincCode, LocalDate from, LocalDate to,
                                                                       int page, int size) {
            return PagedResult.of(List.copyOf(saved), saved.size(), page, size);
        }

        @Override
        public List<ObservationBiologique> findByDemandeExamenId(UUID demandeExamenId, CenterId centerId) {
            return saved.stream().filter(o -> o.getDemandeExamenId().map(demandeExamenId::equals).orElse(false)).toList();
        }

        @Override
        public Optional<ObservationBiologique> findById(UUID id, UUID patientId, CenterId centerId) {
            return saved.stream().filter(o -> o.getId().equals(id)).findFirst();
        }

        @Override
        public ObservationBiologique save(ObservationBiologique observation) {
            saved.removeIf(o -> o.getId().equals(observation.getId()));
            saved.add(observation);
            return observation;
        }

        @Override
        public void deleteById(UUID id, UUID patientId, CenterId centerId) {
            saved.removeIf(o -> o.getId().equals(id));
        }
    }
}
