package com.hemodialyse.backend.contexts.gmao.infrastructure.evenements;

import com.hemodialyse.backend.contexts.gmao.GmaoTestApplication;
import com.hemodialyse.backend.contexts.gmao.application.equipement.EnregistrerEquipement;
import com.hemodialyse.backend.contexts.gmao.application.equipement.EnregistrerEquipementCommande;
import com.hemodialyse.backend.contexts.gmao.application.intervenant.EnregistrerIntervenant;
import com.hemodialyse.backend.contexts.gmao.application.intervenant.EnregistrerIntervenantCommande;
import com.hemodialyse.backend.contexts.gmao.application.intervention.DemarrerIntervention;
import com.hemodialyse.backend.contexts.gmao.application.intervention.InterventionVue;
import com.hemodialyse.backend.contexts.gmao.application.intervention.PlanifierIntervention;
import com.hemodialyse.backend.contexts.gmao.application.intervention.PlanifierInterventionCommande;
import com.hemodialyse.backend.contexts.gmao.application.intervention.TerminerIntervention;
import com.hemodialyse.backend.contexts.gmao.application.intervention.TerminerInterventionCommande;
import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.UtilisateurId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementId;
import com.hemodialyse.backend.contexts.gmao.domain.intervenant.IntervenantId;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.InterventionId;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.InterventionTerminee;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * Outbox (ADR 0013) pour l'intervention : l'intervention, l'équipement qui prend l'état constaté, son historique de statuts
 * et la publication des événements s'écrivent dans la même transaction, ou pas du tout (KB-GMAO-56 : une seule transaction).
 * Un consommateur de test qui échoue garde sa publication incomplète dans {@code event_publication}, avec la donnée de
 * l'événement.
 */
@SpringBootTest(classes = {GmaoTestApplication.class, RegistreDEvenementsInterventionTest.ConsommateurEnEchec.class})
class RegistreDEvenementsInterventionTest {

    private static final CentreId CENTRE = new CentreId(UUID.fromString("99996400-0000-0000-0000-00000000000a"));
    private static final UtilisateurId PAR = new UtilisateurId(UUID.fromString("99996400-0000-0000-0000-0000000000f1"));
    private static final Instant DEBUT = Instant.parse("2026-03-10T08:00:00Z");

    @TestConfiguration
    static class ConsommateurEnEchec {
        @Bean
        Echouant echouantPourIntervention() {
            return new Echouant();
        }
    }

    static class Echouant {
        @ApplicationModuleListener
        void surInterventionTerminee(InterventionTerminee evenement) {
            throw new IllegalStateException("consommateur de test en échec");
        }
    }

    @Autowired
    private EnregistrerEquipement enregistrerEquipement;
    @Autowired
    private EnregistrerIntervenant enregistrerIntervenant;
    @Autowired
    private PlanifierIntervention planifier;
    @Autowired
    private DemarrerIntervention demarrer;
    @Autowired
    private TerminerIntervention terminer;
    @Autowired
    private TransactionTemplate transaction;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    @AfterEach
    void vider() {
        jdbc.update("DELETE FROM event_publication");
        jdbc.update("DELETE FROM gmao_rectifications_intervention WHERE intervention_id IN "
                + "(SELECT id FROM gmao_interventions WHERE centre_id = ?)", CENTRE.valeur());
        jdbc.update("DELETE FROM gmao_lignes_cout_intervention WHERE intervention_id IN "
                + "(SELECT id FROM gmao_interventions WHERE centre_id = ?)", CENTRE.valeur());
        jdbc.update("DELETE FROM gmao_interventions WHERE centre_id = ?", CENTRE.valeur());
        jdbc.update("DELETE FROM gmao_equipement_statut_historique WHERE centre_id = ?", CENTRE.valeur());
        jdbc.update("DELETE FROM gmao_equipements WHERE centre_id = ?", CENTRE.valeur());
        jdbc.update("DELETE FROM gmao_intervenants WHERE centre_id = ?", CENTRE.valeur());
    }

    private InterventionVue interventionEnCours() {
        EquipementId equipement = new EquipementId(enregistrerEquipement.executer(new EnregistrerEquipementCommande(CENTRE,
                "G-OUTBOX", "Générateur", "GENERATEUR_DIALYSE", null, null, null, Instant.parse("2024-03-01T08:00:00Z"), null,
                null, new BigDecimal("100"), PAR)).id());
        IntervenantId intervenant = new IntervenantId(enregistrerIntervenant.executer(new EnregistrerIntervenantCommande(CENTRE,
                "Bernard", "INTERNE", null, null, new BigDecimal("35.00"), PAR)).id());
        InterventionVue vue = planifier.executer(new PlanifierInterventionCommande(equipement, CENTRE, "CURATIVE", DEBUT,
                "Fuite", intervenant, "EN_MAINTENANCE", null, null, null, PAR));
        demarrer.executer(new InterventionId(vue.id()), CENTRE, PAR);
        return vue;
    }

    private InterventionVue terminerLIntervention(InterventionVue vue) {
        return terminer.executer(new TerminerInterventionCommande(new InterventionId(vue.id()), CENTRE, "Joint remplacé",
                "EN_SERVICE", DEBUT.plus(Duration.ofMinutes(20)), null, PAR));
    }

    @Test
    void should_record_the_closing_event_with_the_data_when_the_use_case_commits() {
        InterventionVue vue = interventionEnCours();

        terminerLIntervention(vue);

        assertThat(statutIntervention(vue)).isEqualTo("TERMINEE");
        assertThat(statutEquipement()).isEqualTo("EN_SERVICE");
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            List<String> incompletes = jdbc.queryForList(
                    "SELECT event_type FROM event_publication WHERE completion_date IS NULL", String.class);
            assertThat(incompletes).singleElement().asString().endsWith("InterventionTerminee");
        });
        String donnee = jdbc.queryForObject("SELECT serialized_event FROM event_publication WHERE completion_date IS NULL",
                String.class);
        assertThat(donnee).contains(vue.id().toString()).contains(CENTRE.valeur().toString()).contains("CURATIVE")
                .contains("11.5500");
    }

    @Test
    void should_write_the_intervention_the_equipment_its_history_and_the_events_together_or_not_at_all() {
        InterventionVue vue = interventionEnCours();
        int historique = nombre("gmao_equipement_statut_historique");
        jdbc.update("DELETE FROM event_publication");

        assertThatThrownBy(() -> transaction.executeWithoutResult(statut -> {
            terminerLIntervention(vue);
            throw new IllegalStateException("annulation");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(statutIntervention(vue)).isEqualTo("EN_COURS");
        assertThat(statutEquipement()).isEqualTo("EN_MAINTENANCE");
        assertThat(nombre("gmao_equipement_statut_historique")).isEqualTo(historique);
        assertThat(nombre("gmao_lignes_cout_intervention")).isZero();
        assertThat(nombre("event_publication")).isZero();
    }

    @Test
    void should_acknowledge_and_remove_the_publications_of_the_journal() {
        InterventionVue vue = interventionEnCours();

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(jdbc.queryForList("SELECT listener_id FROM event_publication", String.class))
                        .noneMatch(l -> l.contains("JournalDesEvenements")));
        assertThat(vue.statut().name()).isEqualTo("PLANIFIEE");
    }

    private String statutIntervention(InterventionVue vue) {
        return jdbc.queryForObject("SELECT statut FROM gmao_interventions WHERE id = ?", String.class, vue.id());
    }

    private String statutEquipement() {
        return jdbc.queryForObject("SELECT statut FROM gmao_equipements WHERE centre_id = ?", String.class, CENTRE.valeur());
    }

    private int nombre(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }
}
