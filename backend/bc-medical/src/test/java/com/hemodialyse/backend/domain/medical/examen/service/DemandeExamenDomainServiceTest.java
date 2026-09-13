package com.hemodialyse.backend.domain.medical.examen.service;

import com.hemodialyse.backend.domain.medical.examen.aggregate.DemandeExamen;
import com.hemodialyse.backend.domain.medical.examen.entity.LigneDemandeExamen;
import com.hemodialyse.backend.domain.medical.examen.port.DemandeExamenRepositoryPort;
import com.hemodialyse.backend.domain.medical.examen.valueobject.CategorieExamen;
import com.hemodialyse.backend.domain.medical.examen.valueobject.StatutDemandeExamen;
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

class DemandeExamenDomainServiceTest {

    private final CenterId centerId = CenterId.of(UUID.randomUUID());
    private final UUID patientId = UUID.randomUUID();
    private FakeRepository repository;
    private DemandeExamenDomainService service;

    @BeforeEach
    void setUp() {
        repository = new FakeRepository();
        service = new DemandeExamenDomainService(repository);
    }

    @Test
    void create_should_reject_demande_without_lignes() {
        assertThatThrownBy(() -> service.create(centerId, patientId, "medecin-1", LocalDate.now(),
                CategorieExamen.BIOLOGIE, false, "Bilan de routine", List.of()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void create_should_persist_demande_with_statut_demande() {
        DemandeExamen demande = createSimpleDemande();

        assertThat(demande.getStatut()).isEqualTo(StatutDemandeExamen.DEMANDE);
        assertThat(demande.getLignes()).hasSize(1);
    }

    @Test
    void full_lifecycle_should_follow_allowed_transitions() {
        DemandeExamen demande = createSimpleDemande();

        service.preleve(centerId, patientId, demande.getId());
        service.marquerResultatDisponible(centerId, patientId, demande.getId());
        DemandeExamen validee = service.valider(centerId, patientId, demande.getId(), "RAS");

        assertThat(validee.getStatut()).isEqualTo(StatutDemandeExamen.VALIDE);
        assertThat(validee.getConclusion()).isEqualTo("RAS");
    }

    @Test
    void annuler_should_be_rejected_once_resultat_is_disponible() {
        DemandeExamen demande = createSimpleDemande();
        service.preleve(centerId, patientId, demande.getId());
        service.marquerResultatDisponible(centerId, patientId, demande.getId());

        assertThatThrownBy(() -> service.annuler(centerId, patientId, demande.getId()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void annuler_should_be_allowed_before_preleve() {
        DemandeExamen demande = createSimpleDemande();

        DemandeExamen annulee = service.annuler(centerId, patientId, demande.getId());

        assertThat(annulee.getStatut()).isEqualTo(StatutDemandeExamen.ANNULE);
    }

    @Test
    void valider_should_be_rejected_directly_from_demande() {
        DemandeExamen demande = createSimpleDemande();

        assertThatThrownBy(() -> service.valider(centerId, patientId, demande.getId(), "RAS"))
                .isInstanceOf(BusinessException.class);
    }

    private DemandeExamen createSimpleDemande() {
        ConceptCode hb = ConceptCode.of(CodingSystem.LOINC, "718-7", "Hémoglobine");
        LigneDemandeExamen ligne = LigneDemandeExamen.creer(hb, null, null);
        return service.create(centerId, patientId, "medecin-1", LocalDate.now(),
                CategorieExamen.BIOLOGIE, false, "Bilan de routine", List.of(ligne));
    }

    private static final class FakeRepository implements DemandeExamenRepositoryPort {
        private final List<DemandeExamen> saved = new ArrayList<>();

        @Override
        public PagedResult<DemandeExamen> findPagedByPatientId(UUID patientId, CenterId centerId, int page, int size) {
            return PagedResult.of(List.copyOf(saved), saved.size(), page, size);
        }

        @Override
        public Optional<DemandeExamen> findById(UUID id, UUID patientId, CenterId centerId) {
            return saved.stream().filter(d -> d.getId().equals(id)).findFirst();
        }

        @Override
        public DemandeExamen save(DemandeExamen demande) {
            saved.removeIf(d -> d.getId().equals(demande.getId()));
            saved.add(demande);
            return demande;
        }
    }
}
