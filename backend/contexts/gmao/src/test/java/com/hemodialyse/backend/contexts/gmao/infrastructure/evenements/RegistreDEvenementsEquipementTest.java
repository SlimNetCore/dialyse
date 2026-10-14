package com.hemodialyse.backend.contexts.gmao.infrastructure.evenements;

import com.hemodialyse.backend.contexts.gmao.GmaoTestApplication;
import com.hemodialyse.backend.contexts.gmao.application.equipement.EnregistrerEquipement;
import com.hemodialyse.backend.contexts.gmao.application.equipement.EnregistrerEquipementCommande;
import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.UtilisateurId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementEnregistre;
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
 * Outbox (ADR 0013) pour l'équipement : l'équipement, son historique de statuts et l'événement s'écrivent dans la même
 * transaction, ou pas du tout. Un consommateur de test qui échoue garde sa publication incomplète dans
 * {@code event_publication}, avec la donnée de l'événement.
 */
@SpringBootTest(classes = {GmaoTestApplication.class, RegistreDEvenementsEquipementTest.ConsommateurEnEchec.class})
class RegistreDEvenementsEquipementTest {

    private static final CentreId CENTRE = new CentreId(UUID.fromString("99996100-0000-0000-0000-00000000000a"));
    private static final UtilisateurId PAR = new UtilisateurId(UUID.fromString("99996100-0000-0000-0000-0000000000f1"));

    @TestConfiguration
    static class ConsommateurEnEchec {
        @Bean
        Echouant echouantPourEquipement() {
            return new Echouant();
        }
    }

    static class Echouant {
        @ApplicationModuleListener
        void surEquipementEnregistre(EquipementEnregistre evenement) {
            throw new IllegalStateException("consommateur de test en échec");
        }
    }

    @Autowired
    private EnregistrerEquipement enregistrer;
    @Autowired
    private TransactionTemplate transaction;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    @AfterEach
    void vider() {
        jdbc.update("DELETE FROM event_publication");
        jdbc.update("DELETE FROM gmao_equipement_statut_historique WHERE centre_id = ?", CENTRE.valeur());
        jdbc.update("DELETE FROM gmao_equipements WHERE centre_id = ?", CENTRE.valeur());
    }

    private void unEquipement(String code) {
        enregistrer.executer(new EnregistrerEquipementCommande(CENTRE, code, "Générateur", "GENERATEUR_DIALYSE", null, null,
                null, Instant.parse("2024-03-01T08:00:00Z"), null, null, new BigDecimal("100"), PAR));
    }

    @Test
    void should_record_the_event_with_the_data_when_the_use_case_commits() {
        unEquipement("G-OUTBOX");

        assertThat(count("gmao_equipements")).isEqualTo(1);
        assertThat(count("gmao_equipement_statut_historique")).isEqualTo(1);
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            List<String> incompletes = jdbc.queryForList(
                    "SELECT event_type FROM event_publication WHERE completion_date IS NULL", String.class);
            assertThat(incompletes).singleElement().asString().endsWith("EquipementEnregistre");
        });
        String donnee = jdbc.queryForObject("SELECT serialized_event FROM event_publication WHERE completion_date IS NULL",
                String.class);
        assertThat(donnee).contains("G-OUTBOX").contains("GENERATEUR_DIALYSE").contains(CENTRE.valeur().toString());
    }

    @Test
    void should_record_nothing_when_the_transaction_rolls_back() {
        assertThatThrownBy(() -> transaction.executeWithoutResult(statut -> {
            unEquipement("G-ANNULE");
            throw new IllegalStateException("annulation");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(count("gmao_equipements")).isZero();
        assertThat(count("gmao_equipement_statut_historique")).isZero();
        assertThat(count("event_publication")).isZero();
    }

    @Test
    void should_acknowledge_and_remove_the_publications_of_the_journal() {
        unEquipement("G-JOURNAL");

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(jdbc.queryForList("SELECT listener_id FROM event_publication", String.class))
                        .noneMatch(l -> l.contains("JournalDesEvenements")));
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }
}
