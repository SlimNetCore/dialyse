package com.hemodialyse.backend.domain.medical.antecedent.service;

import com.hemodialyse.backend.domain.medical.antecedent.aggregate.Antecedent;
import com.hemodialyse.backend.domain.medical.antecedent.port.AntecedentRepositoryPort;
import com.hemodialyse.backend.domain.medical.antecedent.valueobject.StatutClinique;
import com.hemodialyse.backend.domain.medical.antecedent.valueobject.TypeAntecedent;
import com.hemodialyse.backend.domain.medical.shared.valueobject.CodingSystem;
import com.hemodialyse.backend.domain.medical.shared.valueobject.ConceptCode;
import com.hemodialyse.backend.domain.shared.PagedResult;
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

class AntecedentDomainServiceTest {

    private final CenterId centerId = CenterId.of(UUID.randomUUID());
    private final UUID patientId = UUID.randomUUID();
    private FakeRepository repository;
    private AntecedentDomainService service;

    @BeforeEach
    void setUp() {
        repository = new FakeRepository();
        service = new AntecedentDomainService(repository);
    }

    @Test
    void create_should_persist_antecedent_with_cim10_diagnostic() {
        ConceptCode diagnostic = ConceptCode.of(CodingSystem.CIM10, "N18.5", "IRC stade 5");

        Antecedent created = service.create(centerId, patientId, TypeAntecedent.MEDICAL, diagnostic, null,
                LocalDate.of(2020, 1, 1), null, null, null);

        assertThat(created.getDiagnostic()).isEqualTo(diagnostic);
        assertThat(created.getStatutClinique()).isEqualTo(StatutClinique.ACTIF);
        assertThat(repository.saved).containsExactly(created);
    }

    @Test
    void create_should_reject_duplicate_active_diagnostic() {
        ConceptCode diagnostic = ConceptCode.of(CodingSystem.CIM10, "N18.5", "IRC stade 5");
        service.create(centerId, patientId, TypeAntecedent.MEDICAL, diagnostic, null,
                LocalDate.of(2020, 1, 1), null, null, null);

        assertThatThrownBy(() -> service.create(centerId, patientId, TypeAntecedent.MEDICAL, diagnostic, null,
                LocalDate.of(2021, 1, 1), null, null, null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void create_should_allow_same_diagnostic_when_previous_is_resolved() {
        ConceptCode diagnostic = ConceptCode.of(CodingSystem.CIM10, "N18.5", "IRC stade 5");
        Antecedent first = service.create(centerId, patientId, TypeAntecedent.MEDICAL, diagnostic, null,
                LocalDate.of(2020, 1, 1), null, null, null);
        service.resoudre(centerId, patientId, first.getId(), LocalDate.of(2020, 6, 1));

        Antecedent second = service.create(centerId, patientId, TypeAntecedent.MEDICAL, diagnostic, null,
                LocalDate.of(2021, 1, 1), null, null, null);

        assertThat(second.getStatutClinique()).isEqualTo(StatutClinique.ACTIF);
    }

    @Test
    void resoudre_should_mark_statut_resolu_and_set_end_date() {
        Antecedent created = service.create(centerId, patientId, TypeAntecedent.MEDICAL, null, "Diabète",
                LocalDate.of(2020, 1, 1), null, null, null);

        Antecedent resolved = service.resoudre(centerId, patientId, created.getId(), LocalDate.of(2023, 5, 1));

        assertThat(resolved.getStatutClinique()).isEqualTo(StatutClinique.RESOLU);
        assertThat(resolved.getPeriode().fin()).isEqualTo(LocalDate.of(2023, 5, 1));
    }

    @Test
    void resoudre_should_fail_when_antecedent_not_found() {
        assertThatThrownBy(() -> service.resoudre(centerId, patientId, UUID.randomUUID(), LocalDate.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static final class FakeRepository implements AntecedentRepositoryPort {
        private final List<Antecedent> saved = new ArrayList<>();

        @Override
        public List<Antecedent> findByPatientId(UUID patientId, CenterId centerId) {
            return List.copyOf(saved);
        }

        @Override
        public PagedResult<Antecedent> findPagedByPatientId(UUID patientId, CenterId centerId, int page, int size) {
            return PagedResult.of(List.copyOf(saved), saved.size(), page, size);
        }

        @Override
        public Optional<Antecedent> findById(UUID id, UUID patientId, CenterId centerId) {
            return saved.stream().filter(a -> a.getId().equals(id)).findFirst();
        }

        @Override
        public Antecedent save(Antecedent antecedent) {
            saved.removeIf(a -> a.getId().equals(antecedent.getId()));
            saved.add(antecedent);
            return antecedent;
        }

        @Override
        public void deleteById(UUID id, UUID patientId, CenterId centerId) {
            saved.removeIf(a -> a.getId().equals(id));
        }
    }
}
