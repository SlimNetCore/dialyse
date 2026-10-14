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
import org.springframework.modulith.events.IncompleteEventPublications;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Une publication incomplète (écouteur en échec) reste dans {@code event_publication} ; rejouée (à la demande, comme au
 * redémarrage du registre), elle est relue depuis sa forme sérialisée, livrée à l'écouteur puis acquittée (supprimée).
 */
@SpringBootTest(classes = {GmaoTestApplication.class, RejeuDesEvenementsTest.EcouteurQuiEchoueUneFois.class})
class RejeuDesEvenementsTest {

    private static final CentreId CENTRE = new CentreId(UUID.fromString("99996100-0000-0000-0000-0000000000b1"));
    private static final UtilisateurId PAR = new UtilisateurId(UUID.fromString("99996100-0000-0000-0000-0000000000f2"));
    private static final String ECOUTEUR = "%QuiEchoueUneFois%";

    @TestConfiguration
    static class EcouteurQuiEchoueUneFois {
        @Bean
        QuiEchoueUneFois quiEchoueUneFois() {
            return new QuiEchoueUneFois();
        }
    }

    static class QuiEchoueUneFois {
        private final AtomicInteger appels = new AtomicInteger();
        private final List<EquipementEnregistre> recus = new CopyOnWriteArrayList<>();

        /** Lu par une méthode : l'écouteur est un proxy, ses champs ne sont pas ceux de l'objet cible. */
        public List<EquipementEnregistre> recus() {
            return recus;
        }

        @ApplicationModuleListener
        void surEquipementEnregistre(EquipementEnregistre evenement) {
            if (appels.incrementAndGet() == 1) {
                throw new IllegalStateException("échec du premier essai");
            }
            recus.add(evenement);
        }
    }

    @Autowired
    private EnregistrerEquipement enregistrer;
    @Autowired
    private IncompleteEventPublications publicationsIncompletes;
    @Autowired
    private QuiEchoueUneFois ecouteur;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    @AfterEach
    void vider() {
        jdbc.update("DELETE FROM event_publication");
        jdbc.update("DELETE FROM gmao_equipement_statut_historique WHERE centre_id = ?", CENTRE.valeur());
        jdbc.update("DELETE FROM gmao_equipements WHERE centre_id = ?", CENTRE.valeur());
    }

    @Test
    void should_replay_an_incomplete_publication_on_demand_and_acknowledge_it() {
        enregistrer.executer(new EnregistrerEquipementCommande(CENTRE, "G-REJEU", "Générateur", "GENERATEUR_DIALYSE", null, null,
                null, Instant.parse("2024-03-01T08:00:00Z"), null, null, new BigDecimal("100"), PAR));
        await().atMost(Duration.ofSeconds(10)).until(() -> incompletes() == 1);
        assertThat(ecouteur.recus()).isEmpty();

        publicationsIncompletes.resubmitIncompletePublications(publication -> true);

        await().atMost(Duration.ofSeconds(10)).until(() -> incompletes() == 0);
        assertThat(ecouteur.recus()).singleElement().satisfies(evenement -> {
            assertThat(evenement.code().valeur()).isEqualTo("G-REJEU");
            assertThat(evenement.centreId()).isEqualTo(CENTRE);
        });
    }

    private int incompletes() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM event_publication WHERE completion_date IS NULL AND listener_id LIKE ?",
                Integer.class, ECOUTEUR);
    }
}
