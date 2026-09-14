package com.hemodialyse.backend.domain.medical.greffe.service;

import com.hemodialyse.backend.domain.medical.greffe.aggregate.BilanPreGreffe;
import com.hemodialyse.backend.domain.medical.greffe.port.BilanPreGreffeRepositoryPort;
import com.hemodialyse.backend.domain.medical.greffe.valueobject.AvisRcp;
import com.hemodialyse.backend.domain.medical.greffe.valueobject.StatutBilanGreffe;
import com.hemodialyse.backend.domain.shared.exception.BusinessException;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BilanPreGreffeDomainServiceTest {

    private final CenterId centerId = CenterId.of(UUID.randomUUID());
    private final UUID patientId = UUID.randomUUID();
    private FakeRepository repository;
    private BilanPreGreffeDomainService service;

    @BeforeEach
    void setUp() {
        repository = new FakeRepository();
        service = new BilanPreGreffeDomainService(repository);
    }

    @Test
    void getOrCreate_should_open_new_bilan_when_none_exists() {
        BilanPreGreffe bilan = service.getOrCreate(centerId, patientId);

        assertThat(bilan.getStatut()).isEqualTo(StatutBilanGreffe.NON_DEBUTE);
        assertThat(bilan.getPatientId()).isEqualTo(patientId);
    }

    @Test
    void changerStatut_should_reject_inscription_liste_attente_without_passing_by_eligible() {
        assertThatThrownBy(() -> service.changerStatut(centerId, patientId, StatutBilanGreffe.INSCRIT_LISTE_ATTENTE))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void changerStatut_should_allow_progression_through_eligible_then_inscription() {
        service.changerStatut(centerId, patientId, StatutBilanGreffe.BILAN_EN_COURS);
        service.changerStatut(centerId, patientId, StatutBilanGreffe.ELIGIBLE);
        BilanPreGreffe bilan = service.changerStatut(centerId, patientId, StatutBilanGreffe.INSCRIT_LISTE_ATTENTE);

        assertThat(bilan.getStatut()).isEqualTo(StatutBilanGreffe.INSCRIT_LISTE_ATTENTE);
        assertThat(bilan.getDateInscriptionListeAttente()).isEqualTo(LocalDate.now());
    }

    @Test
    void changerStatut_should_reject_transition_out_of_contre_indication_definitive() {
        service.changerStatut(centerId, patientId, StatutBilanGreffe.CONTRE_INDICATION_DEFINITIVE);

        assertThatThrownBy(() -> service.changerStatut(centerId, patientId, StatutBilanGreffe.BILAN_EN_COURS))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void ajouterDecisionRcp_should_append_decision_to_bilan() {
        BilanPreGreffe bilan = service.ajouterDecisionRcp(centerId, patientId, LocalDate.now(), AvisRcp.FAVORABLE,
                "RAS", null);

        assertThat(bilan.getDecisionsRcp()).hasSize(1);
        assertThat(bilan.getDecisionsRcp().get(0).getAvis()).isEqualTo(AvisRcp.FAVORABLE);
    }

    private static final class FakeRepository implements BilanPreGreffeRepositoryPort {
        private final Map<UUID, BilanPreGreffe> saved = new HashMap<>();

        @Override
        public Optional<BilanPreGreffe> findByPatientId(UUID patientId, CenterId centerId) {
            return Optional.ofNullable(saved.get(patientId));
        }

        @Override
        public BilanPreGreffe save(BilanPreGreffe bilan) {
            saved.put(bilan.getPatientId(), bilan);
            return bilan;
        }
    }
}
