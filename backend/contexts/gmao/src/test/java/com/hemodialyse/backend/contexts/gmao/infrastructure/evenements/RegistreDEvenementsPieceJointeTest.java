package com.hemodialyse.backend.contexts.gmao.infrastructure.evenements;

import com.hemodialyse.backend.contexts.gmao.GmaoTestApplication;
import com.hemodialyse.backend.contexts.gmao.application.equipement.EnregistrerEquipement;
import com.hemodialyse.backend.contexts.gmao.application.equipement.EnregistrerEquipementCommande;
import com.hemodialyse.backend.contexts.gmao.application.intervention.InterventionVue;
import com.hemodialyse.backend.contexts.gmao.application.intervention.PlanifierIntervention;
import com.hemodialyse.backend.contexts.gmao.application.intervention.PlanifierInterventionCommande;
import com.hemodialyse.backend.contexts.gmao.application.piecejointe.JoindrePieceJointe;
import com.hemodialyse.backend.contexts.gmao.application.piecejointe.JoindrePieceJointeCommande;
import com.hemodialyse.backend.contexts.gmao.application.piecejointe.PieceJointeVue;
import com.hemodialyse.backend.contexts.gmao.application.piecejointe.RetirerPieceJointe;
import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.UtilisateurId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementId;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.InterventionId;
import com.hemodialyse.backend.contexts.gmao.domain.piecejointe.PieceJointeAjoutee;
import com.hemodialyse.backend.contexts.gmao.domain.piecejointe.PieceJointeId;
import com.hemodialyse.backend.contexts.gmao.domain.piecejointe.QuotaDePiecesAtteint;
import com.hemodialyse.backend.contexts.gmao.doubles.Fichiers;
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
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * Outbox (ADR 0013) pour les pièces jointes (R22, R23) : la pièce et la publication de l'événement s'écrivent dans la même
 * transaction, ou pas du tout ; un consommateur de test qui échoue garde sa publication incomplète, avec les données de
 * l'événement mais sans les octets. Et le quota de 50 tient sous concurrence (KB-GMAO-58) : deux envois simultanés au 50e
 * emplacement, un seul passe, parce que l'intervention est verrouillée le temps de compter puis d'insérer.
 */
@SpringBootTest(classes = {GmaoTestApplication.class, RegistreDEvenementsPieceJointeTest.ConsommateurEnEchec.class})
class RegistreDEvenementsPieceJointeTest {

    private static final CentreId CENTRE = new CentreId(UUID.fromString("99996500-0000-0000-0000-00000000000a"));
    private static final UtilisateurId PAR = new UtilisateurId(UUID.fromString("99996500-0000-0000-0000-0000000000f1"));
    private static final Instant DEBUT = Instant.parse("2026-03-10T08:00:00Z");

    @TestConfiguration
    static class ConsommateurEnEchec {
        @Bean
        EchouantPourPiece echouantPourPiece() {
            return new EchouantPourPiece();
        }
    }

    static class EchouantPourPiece {
        @ApplicationModuleListener
        void surPieceJointeAjoutee(PieceJointeAjoutee evenement) {
            throw new IllegalStateException("consommateur de test en échec");
        }
    }

    @Autowired
    private EnregistrerEquipement enregistrerEquipement;
    @Autowired
    private PlanifierIntervention planifier;
    @Autowired
    private JoindrePieceJointe joindre;
    @Autowired
    private RetirerPieceJointe retirer;
    @Autowired
    private TransactionTemplate transaction;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    @AfterEach
    void vider() {
        jdbc.update("DELETE FROM event_publication");
        jdbc.update("DELETE FROM gmao_documents_intervention WHERE centre_id = ?", CENTRE.valeur());
        jdbc.update("DELETE FROM gmao_interventions WHERE centre_id = ?", CENTRE.valeur());
        jdbc.update("DELETE FROM gmao_equipement_statut_historique WHERE centre_id = ?", CENTRE.valeur());
        jdbc.update("DELETE FROM gmao_equipements WHERE centre_id = ?", CENTRE.valeur());
    }

    private InterventionId interventionPlanifiee() {
        EquipementId equipement = new EquipementId(enregistrerEquipement.executer(new EnregistrerEquipementCommande(CENTRE,
                "G-OUTBOX-P", "Générateur", "GENERATEUR_DIALYSE", null, null, null, Instant.parse("2024-03-01T08:00:00Z"),
                null, null, new BigDecimal("100"), PAR)).id());
        InterventionVue vue = planifier.executer(new PlanifierInterventionCommande(equipement, CENTRE, "CURATIVE", DEBUT,
                "Fuite", null, "EN_MAINTENANCE", null, null, null, PAR));
        return new InterventionId(vue.id());
    }

    private PieceJointeVue joindre(InterventionId intervention, String nom) {
        return joindre.executer(new JoindrePieceJointeCommande(intervention, CENTRE, "AUTRE", nom, Fichiers.pdf(), PAR));
    }

    private int nombreDePieces(InterventionId intervention) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM gmao_documents_intervention WHERE intervention_id = ?", Integer.class,
                intervention.valeur());
    }

    @Test
    void should_keep_the_incomplete_publication_with_the_piece_data_but_not_its_bytes_when_a_consumer_fails() {
        InterventionId id = interventionPlanifiee();
        PieceJointeVue vue = joindre(id, "devis.pdf");

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            List<String> incompletes = jdbc.queryForList(
                    "SELECT event_type FROM event_publication WHERE completion_date IS NULL", String.class);
            assertThat(incompletes).singleElement().asString().endsWith("PieceJointeAjoutee");
        });
        String donnee = jdbc.queryForObject("SELECT serialized_event FROM event_publication WHERE completion_date IS NULL",
                String.class);
        assertThat(donnee).contains(id.valeur().toString()).contains(vue.id().toString()).contains(CENTRE.valeur().toString())
                .contains("devis.pdf").doesNotContain("%PDF");
    }

    @Test
    void should_write_the_piece_and_its_event_together_or_not_at_all() {
        InterventionId id = interventionPlanifiee();
        jdbc.update("DELETE FROM event_publication");

        assertThatThrownBy(() -> transaction.executeWithoutResult(statut -> {
            joindre(id, "devis.pdf");
            throw new IllegalStateException("annulation");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(nombreDePieces(id)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM event_publication", Integer.class)).isZero();
    }

    @Test
    void should_register_piece_jointe_retiree_and_delete_the_row_in_the_same_transaction() {
        InterventionId id = interventionPlanifiee();
        PieceJointeVue vue = joindre(id, "devis.pdf");
        jdbc.update("DELETE FROM event_publication");

        assertThatThrownBy(() -> transaction.executeWithoutResult(statut -> {
            retirer.executer(id, CENTRE, new PieceJointeId(vue.id()), PAR);
            throw new IllegalStateException("annulation");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(nombreDePieces(id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM event_publication", Integer.class)).isZero();

        retirer.executer(id, CENTRE, new PieceJointeId(vue.id()), PAR);
        assertThat(nombreDePieces(id)).isZero();
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(jdbc.queryForList("SELECT event_type FROM event_publication", String.class))
                        .noneMatch(t -> t.endsWith("PieceJointeRetiree")));
    }

    @Test
    void should_hold_the_quota_of_50_when_two_uploads_race_for_the_last_place() throws Exception {
        for (int manche = 1; manche <= 3; manche++) {
            InterventionId id = interventionPlanifiee();
            for (int i = 0; i < 49; i++) semer(id, i);
            CyclicBarrier depart = new CyclicBarrier(2);
            ExecutorService pool = Executors.newFixedThreadPool(2);
            try {
                List<Future<String>> resultats = new ArrayList<>();
                for (int k = 0; k < 2; k++) {
                    int numero = k;
                    Callable<String> envoi = () -> {
                        depart.await();
                        try {
                            joindre(id, "course-" + numero + ".pdf");
                            return "ok";
                        } catch (QuotaDePiecesAtteint e) {
                            return "quota";
                        }
                    };
                    resultats.add(pool.submit(envoi));
                }
                List<String> issues = new ArrayList<>();
                for (Future<String> f : resultats) {
                    try {
                        issues.add(f.get());
                    } catch (ExecutionException e) {
                        throw new AssertionError("envoi en échec : " + e.getCause(), e.getCause());
                    }
                }
                assertThat(issues).as("manche %d", manche).containsExactlyInAnyOrder("ok", "quota");
                assertThat(nombreDePieces(id)).isEqualTo(50);
            } finally {
                pool.shutdownNow();
            }
            vider();
        }
    }

    private void semer(InterventionId intervention, int rang) {
        jdbc.update("INSERT INTO gmao_documents_intervention (id, intervention_id, centre_id, type, nom, content_type, taille, "
                        + "contenu, ajoute_par, ajoute_le) VALUES (?, ?, ?, 'AUTRE', ?, 'application/pdf', 1, ?, ?, ?)",
                UUID.randomUUID(), intervention.valeur(), CENTRE.valeur(), "seme-" + rang + ".pdf", new byte[]{1},
                PAR.valeur(), Timestamp.from(DEBUT.plusSeconds(rang)));
    }
}
