package com.hemodialyse.backend.contexts.gmao.infrastructure.evenements;

import com.hemodialyse.backend.contexts.gmao.GmaoTestApplication;
import com.hemodialyse.backend.contexts.gmao.application.intervenant.EnregistrerIntervenant;
import com.hemodialyse.backend.contexts.gmao.application.intervenant.EnregistrerIntervenantCommande;
import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.UtilisateurId;
import com.hemodialyse.backend.contexts.gmao.domain.intervenant.IntervenantEnregistre;
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

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * Outbox (ADR 0013) : l'événement est enregistré dans la même transaction que la donnée, ou pas du tout. Un
 * consommateur de test qui échoue garde sa publication incomplète dans {@code event_publication} ; le journal
 * ({@link JournalDesEvenements}) acquitte la sienne, qui est alors supprimée : rien ne s'accumule.
 */
@SpringBootTest(classes = {GmaoTestApplication.class, RegistreDEvenementsTest.ConsommateurEnEchec.class})
class RegistreDEvenementsTest {

    private static final CentreId CENTRE = new CentreId(UUID.fromString("99996000-0000-0000-0000-00000000000a"));
    private static final UtilisateurId PAR = new UtilisateurId(UUID.fromString("99996000-0000-0000-0000-0000000000f1"));

    @TestConfiguration
    static class ConsommateurEnEchec {
        @Bean
        Echouant echouant() {
            return new Echouant();
        }
    }

    static class Echouant {
        @ApplicationModuleListener
        void surIntervenantEnregistre(IntervenantEnregistre evenement) {
            throw new IllegalStateException("consommateur de test en échec");
        }
    }

    @Autowired
    private EnregistrerIntervenant enregistrer;
    @Autowired
    private TransactionTemplate transaction;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    @AfterEach
    void vider() {
        jdbc.update("DELETE FROM event_publication");
        jdbc.update("DELETE FROM gmao_intervenants WHERE centre_id = ?", CENTRE.valeur());
    }

    @Test
    void should_record_the_event_with_the_data_when_the_use_case_commits() {
        enregistrer.executer(new EnregistrerIntervenantCommande(CENTRE, "Bernard", "INTERNE", null, null, null, PAR));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM gmao_intervenants WHERE centre_id = ?", Integer.class,
                CENTRE.valeur())).isEqualTo(1);
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(incompletes())
                .singleElement().asString().endsWith("IntervenantEnregistre"));
    }

    @Test
    void should_record_nothing_when_the_transaction_rolls_back() {
        assertThatThrownBy(() -> transaction.executeWithoutResult(statut -> {
            enregistrer.executer(new EnregistrerIntervenantCommande(CENTRE, "Annulé", "EXTERNE", null, null, null, PAR));
            throw new IllegalStateException("annulation");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM gmao_intervenants WHERE centre_id = ?", Integer.class,
                CENTRE.valeur())).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM event_publication", Integer.class)).isZero();
    }

    @Test
    void should_acknowledge_and_remove_the_publications_of_the_journal() {
        enregistrer.executer(new EnregistrerIntervenantCommande(CENTRE, "Journal", "INTERNE", null, null, null, PAR));

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(jdbc.queryForList("SELECT listener_id FROM event_publication", String.class))
                        .noneMatch(l -> l.contains("JournalDesEvenements")));
    }

    private List<String> incompletes() {
        return jdbc.queryForList("SELECT event_type FROM event_publication WHERE completion_date IS NULL", String.class);
    }
}
