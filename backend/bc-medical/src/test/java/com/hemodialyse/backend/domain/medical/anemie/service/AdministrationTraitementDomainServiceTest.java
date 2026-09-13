package com.hemodialyse.backend.domain.medical.anemie.service;

import com.hemodialyse.backend.domain.medical.anemie.aggregate.AdministrationTraitement;
import com.hemodialyse.backend.domain.medical.anemie.port.AdministrationTraitementRepositoryPort;
import com.hemodialyse.backend.domain.medical.anemie.valueobject.DoseAdministree;
import com.hemodialyse.backend.domain.medical.anemie.valueobject.TypeTraitementAnemie;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.domain.shared.exception.BusinessException;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdministrationTraitementDomainServiceTest {

    private final CenterId centerId = CenterId.of(UUID.randomUUID());
    private final UUID patientId = UUID.randomUUID();
    private FakeRepository repository;
    private AdministrationTraitementDomainService service;

    @BeforeEach
    void setUp() {
        repository = new FakeRepository();
        service = new AdministrationTraitementDomainService(repository);
    }

    @Test
    void create_should_persist_administered_treatment() {
        AdministrationTraitement administration = service.create(centerId, patientId, null,
                TypeTraitementAnemie.EPO, "Darbepoetine", new DoseAdministree(new BigDecimal("60"), "UI"),
                "SC", LocalDate.now(), null, "infirmier-1", true, null);

        assertThat(administration.isAdministree()).isTrue();
        assertThat(administration.getDose()).isPresent();
    }

    @Test
    void create_should_reject_administered_treatment_without_dose() {
        assertThatThrownBy(() -> service.create(centerId, patientId, null, TypeTraitementAnemie.EPO, "Darbepoetine",
                null, "SC", LocalDate.now(), null, "infirmier-1", true, null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void create_should_require_motif_when_not_administered() {
        assertThatThrownBy(() -> service.create(centerId, patientId, null, TypeTraitementAnemie.FER_INJECTABLE,
                "Fer saccharose", null, "IV", LocalDate.now(), null, "infirmier-1", false, null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void create_should_persist_non_administered_treatment_with_motif() {
        AdministrationTraitement administration = service.create(centerId, patientId, null,
                TypeTraitementAnemie.FER_INJECTABLE, "Fer saccharose", null, "IV", LocalDate.now(), null,
                "infirmier-1", false, "Patient absent");

        assertThat(administration.isAdministree()).isFalse();
        assertThat(administration.getDose()).isEmpty();
        assertThat(administration.getMotifNonAdministration()).isEqualTo("Patient absent");
    }

    private static final class FakeRepository implements AdministrationTraitementRepositoryPort {
        private final List<AdministrationTraitement> saved = new ArrayList<>();

        @Override
        public PagedResult<AdministrationTraitement> findPagedByPatientId(UUID patientId, CenterId centerId, int page, int size) {
            return PagedResult.of(List.copyOf(saved), saved.size(), page, size);
        }

        @Override
        public List<AdministrationTraitement> findByPatientId(UUID patientId, CenterId centerId) {
            return List.copyOf(saved);
        }

        @Override
        public AdministrationTraitement save(AdministrationTraitement administration) {
            saved.add(administration);
            return administration;
        }
    }
}
