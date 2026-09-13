package com.hemodialyse.backend.domain.medical.allergie.service;

import com.hemodialyse.backend.domain.medical.allergie.aggregate.Allergie;
import com.hemodialyse.backend.domain.medical.allergie.port.AllergieRepositoryPort;
import com.hemodialyse.backend.domain.medical.allergie.valueobject.CategorieAllergie;
import com.hemodialyse.backend.domain.medical.allergie.valueobject.CriticiteAllergie;
import com.hemodialyse.backend.domain.medical.allergie.valueobject.StatutVerificationAllergie;
import com.hemodialyse.backend.domain.medical.allergie.valueobject.TypeReaction;
import com.hemodialyse.backend.domain.medical.shared.valueobject.CodingSystem;
import com.hemodialyse.backend.domain.medical.shared.valueobject.ConceptCode;
import com.hemodialyse.backend.domain.shared.PagedResult;
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

class AllergieDomainServiceTest {

    private final CenterId centerId = CenterId.of(UUID.randomUUID());
    private final UUID patientId = UUID.randomUUID();
    private FakeRepository repository;
    private AllergieDomainService service;

    @BeforeEach
    void setUp() {
        repository = new FakeRepository();
        service = new AllergieDomainService(repository);
    }

    @Test
    void create_should_reject_high_criticity_without_manifestations() {
        ConceptCode penicilline = ConceptCode.of(CodingSystem.LOCAL, "PENICILLINE", "Pénicilline");

        assertThatThrownBy(() -> service.create(centerId, patientId, penicilline, CategorieAllergie.MEDICAMENT,
                CriticiteAllergie.HAUTE, TypeReaction.ALLERGIE, null, LocalDate.now(),
                StatutVerificationAllergie.CONFIRMEE))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void create_should_persist_high_criticity_allergy_with_manifestations() {
        ConceptCode penicilline = ConceptCode.of(CodingSystem.LOCAL, "PENICILLINE", "Pénicilline");

        Allergie allergie = service.create(centerId, patientId, penicilline, CategorieAllergie.MEDICAMENT,
                CriticiteAllergie.HAUTE, TypeReaction.ALLERGIE, "Choc anaphylactique", LocalDate.now(),
                StatutVerificationAllergie.CONFIRMEE);

        assertThat(allergie.getCriticite()).isEqualTo(CriticiteAllergie.HAUTE);
        assertThat(service.listCritiquesByPatient(centerId, patientId)).containsExactly(allergie);
    }

    @Test
    void listCritiquesByPatient_should_exclude_low_criticity() {
        ConceptCode pollen = ConceptCode.of(CodingSystem.LOCAL, "POLLEN", "Pollen");
        service.create(centerId, patientId, pollen, CategorieAllergie.ENVIRONNEMENT, CriticiteAllergie.BASSE,
                TypeReaction.ALLERGIE, "Rhinite légère", LocalDate.now(), StatutVerificationAllergie.SUSPECTEE);

        assertThat(service.listCritiquesByPatient(centerId, patientId)).isEmpty();
    }

    @Test
    void update_should_fail_when_allergie_not_found() {
        assertThatThrownBy(() -> service.update(centerId, patientId, UUID.randomUUID(), CriticiteAllergie.BASSE,
                null, StatutVerificationAllergie.CONFIRMEE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static final class FakeRepository implements AllergieRepositoryPort {
        private final List<Allergie> saved = new ArrayList<>();

        @Override
        public List<Allergie> findByPatientId(UUID patientId, CenterId centerId) {
            return List.copyOf(saved);
        }

        @Override
        public PagedResult<Allergie> findPagedByPatientId(UUID patientId, CenterId centerId, int page, int size) {
            return PagedResult.of(List.copyOf(saved), saved.size(), page, size);
        }

        @Override
        public Optional<Allergie> findById(UUID id, UUID patientId, CenterId centerId) {
            return saved.stream().filter(a -> a.getId().equals(id)).findFirst();
        }

        @Override
        public Allergie save(Allergie allergie) {
            saved.removeIf(a -> a.getId().equals(allergie.getId()));
            saved.add(allergie);
            return allergie;
        }

        @Override
        public void deleteById(UUID id, UUID patientId, CenterId centerId) {
            saved.removeIf(a -> a.getId().equals(id));
        }
    }
}
