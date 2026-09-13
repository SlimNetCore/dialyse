package com.hemodialyse.backend.domain.medical.ordonnance.service;

import com.hemodialyse.backend.domain.medical.ordonnance.aggregate.Ordonnance;
import com.hemodialyse.backend.domain.medical.ordonnance.entity.LigneOrdonnance;
import com.hemodialyse.backend.domain.medical.ordonnance.port.OrdonnanceNumeroGeneratorPort;
import com.hemodialyse.backend.domain.medical.ordonnance.port.OrdonnanceRepositoryPort;
import com.hemodialyse.backend.domain.medical.ordonnance.valueobject.StatutOrdonnance;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrdonnanceDomainServiceTest {

    private final CenterId centerId = CenterId.of(UUID.randomUUID());
    private final UUID patientId = UUID.randomUUID();
    private FakeRepository repository;
    private OrdonnanceDomainService service;

    @BeforeEach
    void setUp() {
        repository = new FakeRepository();
        service = new OrdonnanceDomainService(repository, new FakeNumeroGenerator());
    }

    @Test
    void create_should_reject_ordonnance_without_lignes() {
        assertThatThrownBy(() -> service.create(centerId, patientId, "medecin-1", LocalDate.now(), List.of()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void create_should_persist_ordonnance_with_statut_brouillon() {
        Ordonnance ordonnance = createSimpleOrdonnance();

        assertThat(ordonnance.getStatut()).isEqualTo(StatutOrdonnance.BROUILLON);
        assertThat(ordonnance.getNumero()).isNull();
        assertThat(ordonnance.getLignes()).hasSize(1);
    }

    @Test
    void signer_should_assign_numero_and_transition_to_signee() {
        Ordonnance ordonnance = createSimpleOrdonnance();

        Ordonnance signee = service.signer(centerId, patientId, ordonnance.getId());

        assertThat(signee.getStatut()).isEqualTo(StatutOrdonnance.SIGNEE);
        assertThat(signee.getNumero()).isEqualTo("ORD-0001");
        assertThat(signee.getSignedAt()).isNotNull();
    }

    @Test
    void full_lifecycle_should_follow_allowed_transitions() {
        Ordonnance ordonnance = createSimpleOrdonnance();
        service.signer(centerId, patientId, ordonnance.getId());

        Ordonnance imprimee = service.marquerImprimee(centerId, patientId, ordonnance.getId());

        assertThat(imprimee.getStatut()).isEqualTo(StatutOrdonnance.IMPRIMEE);
    }

    @Test
    void marquer_imprimee_should_be_rejected_before_signature() {
        Ordonnance ordonnance = createSimpleOrdonnance();

        assertThatThrownBy(() -> service.marquerImprimee(centerId, patientId, ordonnance.getId()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void annuler_should_be_allowed_after_impression() {
        Ordonnance ordonnance = createSimpleOrdonnance();
        service.signer(centerId, patientId, ordonnance.getId());
        service.marquerImprimee(centerId, patientId, ordonnance.getId());

        Ordonnance annulee = service.annuler(centerId, patientId, ordonnance.getId());

        assertThat(annulee.getStatut()).isEqualTo(StatutOrdonnance.ANNULEE);
    }

    @Test
    void annulee_should_be_terminal() {
        Ordonnance ordonnance = createSimpleOrdonnance();
        service.annuler(centerId, patientId, ordonnance.getId());

        assertThatThrownBy(() -> service.signer(centerId, patientId, ordonnance.getId()))
                .isInstanceOf(BusinessException.class);
    }

    private Ordonnance createSimpleOrdonnance() {
        ConceptCode medicament = ConceptCode.of(CodingSystem.ATC, "B03XA02", "Darbepoetine alfa");
        LigneOrdonnance ligne = LigneOrdonnance.creer(medicament, null, "1 injection SC / semaine",
                "SC", 28, 4, null);
        return service.create(centerId, patientId, "medecin-1", LocalDate.now(), List.of(ligne));
    }

    private static final class FakeNumeroGenerator implements OrdonnanceNumeroGeneratorPort {
        private final AtomicInteger counter = new AtomicInteger(0);

        @Override
        public String genererNumero(CenterId centerId) {
            return "ORD-" + String.format("%04d", counter.incrementAndGet());
        }
    }

    private static final class FakeRepository implements OrdonnanceRepositoryPort {
        private final List<Ordonnance> saved = new ArrayList<>();

        @Override
        public PagedResult<Ordonnance> findPagedByPatientId(UUID patientId, CenterId centerId, int page, int size) {
            return PagedResult.of(List.copyOf(saved), saved.size(), page, size);
        }

        @Override
        public Optional<Ordonnance> findById(UUID id, UUID patientId, CenterId centerId) {
            return saved.stream().filter(o -> o.getId().equals(id)).findFirst();
        }

        @Override
        public Ordonnance save(Ordonnance ordonnance) {
            saved.removeIf(o -> o.getId().equals(ordonnance.getId()));
            saved.add(ordonnance);
            return ordonnance;
        }
    }
}
