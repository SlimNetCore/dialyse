package com.hemodialyse.backend.infrastructure.persistence.adapter.gmao;

import com.hemodialyse.backend.domain.gmao.model.*;
import com.hemodialyse.backend.domain.gmao.port.InterventionRepositoryPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Requêtes de coût de maintenance : seules les interventions en cours/terminées comptent, et tout est
 * cloisonné par centre (AGENTS.md §2).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class InterventionCostQueriesIntegrationTest {

    private static final UUID CENTRE_A = UUID.fromString("99994000-0000-0000-0000-00000000000a");
    private static final UUID CENTRE_B = UUID.fromString("99994000-0000-0000-0000-00000000000b");
    private static final UUID EQUIPEMENT_A = UUID.fromString("99994000-0000-0000-0000-0000000000a1");
    private static final UUID EQUIPEMENT_B = UUID.fromString("99994000-0000-0000-0000-0000000000b1");

    private static final LocalDateTime DEBUT = LocalDateTime.of(2026, 3, 10, 8, 0);
    private static final LocalDateTime FROM = LocalDateTime.of(2026, 1, 1, 0, 0);
    private static final LocalDateTime TO = LocalDateTime.of(2027, 1, 1, 0, 0);

    @Autowired
    private InterventionRepositoryPort interventions;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        cleanup();
        insertEquipement(EQUIPEMENT_A, CENTRE_A, "IC-A");
        insertEquipement(EQUIPEMENT_B, CENTRE_B, "IC-B");

        save(EQUIPEMENT_A, CENTRE_A, StatutIntervention.TERMINEE, "1000");
        save(EQUIPEMENT_A, CENTRE_A, StatutIntervention.EN_COURS, "500");
        save(EQUIPEMENT_A, CENTRE_A, StatutIntervention.ANNULEE, "9000");
        save(EQUIPEMENT_A, CENTRE_A, StatutIntervention.PLANIFIEE, "7000");
        save(EQUIPEMENT_B, CENTRE_B, StatutIntervention.TERMINEE, "2500");
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM gmao_lignes_cout_intervention WHERE intervention_id IN " +
                "(SELECT id FROM gmao_interventions WHERE centre_id IN (?, ?))", CENTRE_A, CENTRE_B);
        jdbc.update("DELETE FROM gmao_interventions WHERE centre_id IN (?, ?)", CENTRE_A, CENTRE_B);
        jdbc.update("DELETE FROM gmao_equipements WHERE id IN (?, ?)", EQUIPEMENT_A, EQUIPEMENT_B);
    }

    @Test
    void sum_should_count_only_in_progress_and_finished_interventions() {
        assertEquals(0, new BigDecimal("1500").compareTo(
                interventions.sumCoutByEquipementIdAndDateRange(EQUIPEMENT_A, FROM, TO)));
        assertEquals(0, new BigDecimal("1500").compareTo(
                interventions.sumCoutByCentreIdAndDateRange(CENTRE_A, FROM, TO)));
        assertEquals(0, new BigDecimal("1500").compareTo(interventions.sumCoutCumuleByEquipementId(EQUIPEMENT_A)));
    }

    @Test
    void grouped_sum_should_be_scoped_to_the_centre() {
        Map<UUID, BigDecimal> parEquipement = interventions.sumCoutParEquipement(CENTRE_A, FROM, TO);

        assertEquals(1, parEquipement.size());
        assertEquals(0, new BigDecimal("1500").compareTo(parEquipement.get(EQUIPEMENT_A)));
        assertNull(parEquipement.get(EQUIPEMENT_B));
        assertEquals(0, new BigDecimal("2500").compareTo(
                interventions.sumCoutCumuleParEquipement(CENTRE_B).get(EQUIPEMENT_B)));
    }

    @Test
    void period_bounds_should_exclude_interventions_started_outside() {
        assertEquals(0, BigDecimal.ZERO.compareTo(
                interventions.sumCoutByEquipementIdAndDateRange(EQUIPEMENT_A, TO, TO.plusYears(1))));
    }

    private void save(UUID equipementId, UUID centreId, StatutIntervention statut, String montant) {
        LigneCoutIntervention ligne = LigneCoutIntervention.creer(
                TypeLigneCout.PIECE, "Pièce", BigDecimal.ONE, new BigDecimal(montant), null);
        interventions.save(Intervention.reconstruct(
                UUID.randomUUID(), equipementId, centreId, TypeIntervention.CURATIVE, statut, DEBUT, null, null,
                "Test coût", null, null, null, DEBUT, DEBUT, UUID.randomUUID(), UUID.randomUUID(), List.of(ligne),
                StatutEquipement.EN_SERVICE, null));
    }

    private void insertEquipement(UUID id, UUID centreId, String code) {
        jdbc.update(
                "INSERT INTO gmao_equipements (id, code, designation, type, centre_id, statut, date_installation, " +
                        "date_creation, cree_par) VALUES (?, ?, ?, 'GENERATEUR_DIALYSE', ?, 'EN_SERVICE', ?, ?, ?)",
                id, code, "Générateur " + code, centreId, LocalDateTime.now(), LocalDateTime.now(), new UUID(0, 0));
    }
}
