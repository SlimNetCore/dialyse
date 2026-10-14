package com.hemodialyse.backend.contexts.gmao.infrastructure.persistence;

import com.hemodialyse.backend.contexts.gmao.GmaoTestApplication;
import com.hemodialyse.backend.contexts.gmao.application.Horloge;
import com.hemodialyse.backend.contexts.gmao.application.equipement.EnregistrerEquipement;
import com.hemodialyse.backend.contexts.gmao.application.equipement.EnregistrerEquipementCommande;
import com.hemodialyse.backend.contexts.gmao.application.intervenant.EnregistrerIntervenant;
import com.hemodialyse.backend.contexts.gmao.application.intervenant.EnregistrerIntervenantCommande;
import com.hemodialyse.backend.contexts.gmao.application.intervention.AjouterTache;
import com.hemodialyse.backend.contexts.gmao.application.intervention.AjouterTacheCommande;
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
import com.hemodialyse.backend.contexts.gmao.infrastructure.systeme.HorlogeSysteme;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Deux commandes simultanées sur la même intervention ne perdent aucune mise à jour (verrou pessimiste, ADR 0024). Scénario
 * déterministe : l'ajout d'une tâche charge l'intervention puis est suspendu (par l'horloge de test) ; pendant ce temps, la
 * clôture est lancée. Sans verrou, la clôture se termine seule et l'ajout, qui avait lu l'ancien état, la réécrit : statut
 * redevenu en cours et ligne de valorisation supprimée. Avec le verrou, la clôture attend la fin de l'ajout.
 */
@SpringBootTest(classes = {GmaoTestApplication.class, ConcurrenceDesCommandesInterventionTest.HorlogeSuspendue.class},
        properties = "spring.datasource.url=jdbc:h2:mem:gmao-concurrence;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=30000")
class ConcurrenceDesCommandesInterventionTest {

    private static final CentreId CENTRE = new CentreId(UUID.fromString("99996600-0000-0000-0000-00000000000a"));
    private static final UtilisateurId PAR = new UtilisateurId(UUID.fromString("99996600-0000-0000-0000-0000000000f1"));
    private static final Instant DEBUT = Instant.parse("2026-03-10T08:00:00Z");
    private static final String FIL_DE_L_AJOUT = "ajout-de-tache";

    /** Suspend le fil de l'ajout de tâche, juste après le chargement de l'intervention, jusqu'à la libération. */
    static class SuspensionDeLAjout {
        static volatile CountDownLatch charge = new CountDownLatch(1);
        static volatile CountDownLatch liberation = new CountDownLatch(1);

        static void reinitialiser() {
            charge = new CountDownLatch(1);
            liberation = new CountDownLatch(1);
        }
    }

    @TestConfiguration
    static class HorlogeSuspendue {
        @Bean
        @Primary
        Horloge horlogeSuspendue() {
            Horloge systeme = new HorlogeSysteme();
            return () -> {
                if (FIL_DE_L_AJOUT.equals(Thread.currentThread().getName())) {
                    SuspensionDeLAjout.charge.countDown();
                    try {
                        SuspensionDeLAjout.liberation.await(30, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                return systeme.maintenant();
            };
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
    private AjouterTache ajouterTache;
    @Autowired
    private TerminerIntervention terminer;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    @AfterEach
    void vider() {
        SuspensionDeLAjout.reinitialiser();
        jdbc.update("DELETE FROM event_publication");
        jdbc.update("DELETE FROM gmao_taches_intervention WHERE centre_id = ?", CENTRE.valeur());
        jdbc.update("DELETE FROM gmao_lignes_cout_intervention WHERE intervention_id IN "
                + "(SELECT id FROM gmao_interventions WHERE centre_id = ?)", CENTRE.valeur());
        jdbc.update("DELETE FROM gmao_interventions WHERE centre_id = ?", CENTRE.valeur());
        jdbc.update("DELETE FROM gmao_equipement_statut_historique WHERE centre_id = ?", CENTRE.valeur());
        jdbc.update("DELETE FROM gmao_equipements WHERE centre_id = ?", CENTRE.valeur());
        jdbc.update("DELETE FROM gmao_intervenants WHERE centre_id = ?", CENTRE.valeur());
    }

    private InterventionId interventionEnCours() {
        EquipementId equipement = new EquipementId(enregistrerEquipement.executer(new EnregistrerEquipementCommande(CENTRE,
                "G-CONCURRENCE", "Générateur", "GENERATEUR_DIALYSE", null, null, null, Instant.parse("2024-03-01T08:00:00Z"),
                null, null, new BigDecimal("100"), PAR)).id());
        IntervenantId intervenant = new IntervenantId(enregistrerIntervenant.executer(new EnregistrerIntervenantCommande(CENTRE,
                "Bernard", "INTERNE", null, null, new BigDecimal("35.00"), PAR)).id());
        InterventionVue vue = planifier.executer(new PlanifierInterventionCommande(equipement, CENTRE, "CURATIVE", DEBUT,
                "Fuite", intervenant, "EN_MAINTENANCE", null, null, null, PAR));
        InterventionId id = new InterventionId(vue.id());
        demarrer.executer(id, CENTRE, PAR);
        return id;
    }

    @Test
    void should_keep_the_closing_and_its_automatic_line_when_a_task_is_added_at_the_same_time() throws Exception {
        InterventionId id = interventionEnCours();

        CompletableFuture<Void> ajout = CompletableFuture.runAsync(
                () -> ajouterTache.executer(new AjouterTacheCommande(id, CENTRE, "Changer le joint", PAR)),
                commande(FIL_DE_L_AJOUT));
        assertThat(SuspensionDeLAjout.charge.await(30, TimeUnit.SECONDS)).as("l'ajout a chargé l'intervention").isTrue();

        CompletableFuture<Void> cloture = CompletableFuture.runAsync(() -> terminer.executer(new TerminerInterventionCommande(id,
                CENTRE, "Joint remplacé", "EN_SERVICE", DEBUT.plus(Duration.ofMinutes(20)), null, PAR)), commande("cloture"));
        try {
            cloture.get(3, TimeUnit.SECONDS);
        } catch (TimeoutException attendue) {
            // avec le verrou, la clôture attend la fin de l'ajout
        }
        SuspensionDeLAjout.liberation.countDown();
        ajout.get(30, TimeUnit.SECONDS);
        cloture.get(30, TimeUnit.SECONDS);

        assertThat(jdbc.queryForObject("SELECT statut FROM gmao_interventions WHERE id = ?", String.class, id.valeur()))
                .isEqualTo("TERMINEE");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM gmao_taches_intervention WHERE intervention_id = ?",
                Integer.class, id.valeur())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM gmao_lignes_cout_intervention WHERE intervention_id = ?",
                Integer.class, id.valeur())).as("ligne de valorisation automatique").isEqualTo(1);
    }

    private static java.util.concurrent.Executor commande(String nom) {
        return travail -> {
            Thread fil = new Thread(travail, nom);
            fil.start();
        };
    }
}
