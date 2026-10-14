package com.hemodialyse.backend.contexts.gmao.infrastructure.evenements;

import com.hemodialyse.backend.contexts.gmao.GmaoTestApplication;
import com.hemodialyse.backend.contexts.gmao.application.equipement.EnregistrerEquipement;
import com.hemodialyse.backend.contexts.gmao.application.equipement.EnregistrerEquipementCommande;
import com.hemodialyse.backend.contexts.gmao.application.intervention.AcheverTache;
import com.hemodialyse.backend.contexts.gmao.application.intervention.AjouterTache;
import com.hemodialyse.backend.contexts.gmao.application.intervention.AjouterTacheCommande;
import com.hemodialyse.backend.contexts.gmao.application.intervention.DemarrerIntervention;
import com.hemodialyse.backend.contexts.gmao.application.intervention.DemarrerTache;
import com.hemodialyse.backend.contexts.gmao.application.intervention.InterventionVue;
import com.hemodialyse.backend.contexts.gmao.application.intervention.PlanifierIntervention;
import com.hemodialyse.backend.contexts.gmao.application.intervention.PlanifierInterventionCommande;
import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.UtilisateurId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementId;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.InterventionId;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.TacheAchevee;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.TacheId;
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
 * Outbox (ADR 0013) pour les tâches d'intervention (R21, KB-GMAO-55) : la tâche et la publication de l'événement
 * s'écrivent dans la même transaction, ou pas du tout. Un consommateur de test qui échoue garde sa publication incomplète
 * dans {@code event_publication}, avec la donnée de l'événement.
 */
@SpringBootTest(classes = {GmaoTestApplication.class, RegistreDEvenementsTacheTest.ConsommateurEnEchec.class})
class RegistreDEvenementsTacheTest {

    private static final CentreId CENTRE = new CentreId(UUID.fromString("99996500-0000-0000-0000-00000000000a"));
    private static final UtilisateurId PAR = new UtilisateurId(UUID.fromString("99996500-0000-0000-0000-0000000000f1"));
    private static final Instant DEBUT = Instant.parse("2026-03-10T08:00:00Z");

    @TestConfiguration
    static class ConsommateurEnEchec {
        @Bean
        EchouantPourTache echouantPourTache() {
            return new EchouantPourTache();
        }
    }

    static class EchouantPourTache {
        @ApplicationModuleListener
        void surTacheAchevee(TacheAchevee evenement) {
            throw new IllegalStateException("consommateur de test en échec");
        }
    }

    @Autowired
    private EnregistrerEquipement enregistrerEquipement;
    @Autowired
    private PlanifierIntervention planifier;
    @Autowired
    private DemarrerIntervention demarrer;
    @Autowired
    private AjouterTache ajouter;
    @Autowired
    private DemarrerTache demarrerTache;
    @Autowired
    private AcheverTache acheverTache;
    @Autowired
    private TransactionTemplate transaction;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    @AfterEach
    void vider() {
        jdbc.update("DELETE FROM event_publication");
        jdbc.update("DELETE FROM gmao_taches_intervention WHERE centre_id = ?", CENTRE.valeur());
        jdbc.update("DELETE FROM gmao_interventions WHERE centre_id = ?", CENTRE.valeur());
        jdbc.update("DELETE FROM gmao_equipement_statut_historique WHERE centre_id = ?", CENTRE.valeur());
        jdbc.update("DELETE FROM gmao_equipements WHERE centre_id = ?", CENTRE.valeur());
    }

    private InterventionId interventionEnCours() {
        EquipementId equipement = new EquipementId(enregistrerEquipement.executer(new EnregistrerEquipementCommande(CENTRE,
                "G-OUTBOX-T", "Générateur", "GENERATEUR_DIALYSE", null, null, null, Instant.parse("2024-03-01T08:00:00Z"),
                null, null, new BigDecimal("100"), PAR)).id());
        InterventionVue vue = planifier.executer(new PlanifierInterventionCommande(equipement, CENTRE, "CURATIVE", DEBUT,
                "Fuite", null, "EN_MAINTENANCE", null, null, null, PAR));
        InterventionId id = new InterventionId(vue.id());
        demarrer.executer(id, CENTRE, PAR);
        return id;
    }

    @Test
    void should_keep_the_incomplete_publication_with_the_task_data_when_a_consumer_fails() {
        InterventionId id = interventionEnCours();
        TacheId tache = new TacheId(ajouter.executer(new AjouterTacheCommande(id, CENTRE, "Changer le joint", PAR))
                .taches().getFirst().id());
        demarrerTache.executer(id, CENTRE, tache, PAR);
        acheverTache.executer(id, CENTRE, tache, PAR);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            List<String> incompletes = jdbc.queryForList(
                    "SELECT event_type FROM event_publication WHERE completion_date IS NULL", String.class);
            assertThat(incompletes).singleElement().asString().endsWith("TacheAchevee");
        });
        String donnee = jdbc.queryForObject("SELECT serialized_event FROM event_publication WHERE completion_date IS NULL",
                String.class);
        assertThat(donnee).contains(id.valeur().toString()).contains(tache.valeur().toString())
                .contains(CENTRE.valeur().toString());
    }

    @Test
    void should_write_the_task_and_its_event_together_or_not_at_all() {
        InterventionId id = interventionEnCours();
        jdbc.update("DELETE FROM event_publication");

        assertThatThrownBy(() -> transaction.executeWithoutResult(statut -> {
            ajouter.executer(new AjouterTacheCommande(id, CENTRE, "Changer le joint", PAR));
            throw new IllegalStateException("annulation");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM gmao_taches_intervention WHERE centre_id = ?", Integer.class,
                CENTRE.valeur())).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM event_publication", Integer.class)).isZero();
    }

    @Test
    void should_persist_the_task_and_register_its_event_when_the_transaction_commits() {
        InterventionId id = interventionEnCours();
        jdbc.update("DELETE FROM event_publication");

        ajouter.executer(new AjouterTacheCommande(id, CENTRE, "Changer le joint", PAR));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM gmao_taches_intervention WHERE centre_id = ?", Integer.class,
                CENTRE.valeur())).isEqualTo(1);
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(jdbc.queryForList("SELECT event_type FROM event_publication", String.class))
                        .noneMatch(t -> t.endsWith("TacheAjoutee")));
    }
}
