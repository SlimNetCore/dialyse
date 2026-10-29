package com.hemodialyse.backend.contexts.planification.contrat;

import com.hemodialyse.backend.contexts.planification.application.LancerUneOptimisation;
import com.hemodialyse.backend.contexts.planification.application.NouveauxIdentifiantsDeLOptimisation;
import com.hemodialyse.backend.contexts.planification.application.Optimiseur;
import com.hemodialyse.backend.contexts.planification.domain.DonneesDOptimisation;
import com.hemodialyse.backend.contexts.planification.domain.EcritureConcurrente;
import com.hemodialyse.backend.contexts.planification.domain.ExecutionDOptimisation;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.ExecutionId;
import com.hemodialyse.backend.contexts.planification.domain.JeuDOptimisation;
import com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces;
import com.hemodialyse.backend.contexts.planification.domain.ParametresDOptimisation;
import com.hemodialyse.backend.contexts.planification.domain.Perimetre;
import com.hemodialyse.backend.contexts.planification.domain.RegleDePlanificationViolee;
import com.hemodialyse.backend.contexts.planification.infrastructure.motdepasse.NouveauxIdentifiantsDeLOptimisationAleatoires;
import com.hemodialyse.backend.contexts.planification.infrastructure.persistence.CalendriersDesPropositionsJdbc;
import com.hemodialyse.backend.contexts.planification.infrastructure.persistence.ExecutionsJdbc;
import com.hemodialyse.backend.contexts.planification.infrastructure.persistence.ReglagesJdbc;
import com.hemodialyse.backend.contexts.planification.infrastructure.persistence.VerrouDesPlacesJdbc;
import com.hemodialyse.backend.contexts.planification.infrastructure.transaction.UniteDeTravailSpring;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * « Un seul calcul par centre » TENU EN BASE, sur un PostgreSQL 16 RÉEL (décision du 28/10, KB-PLN-92, corrige B3) : le verrou du centre
 * ({@code planning_parametres FOR UPDATE}, {@code lock_timeout}) exclut les lancements de plusieurs instances du serveur, simulées ici
 * par des fils qui ne partagent QUE la base. Un lancement qui n'obtient pas le verrou à temps reçoit {@link EcritureConcurrente} (409).
 * Base JETABLE uniquement ({@code planification-p8-pg16-rejeu.sh}).
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=${planification.pg16.url}",
        "spring.datasource.username=${planification.pg16.user:planification_test}",
        "spring.datasource.password=${planification.pg16.password:}",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.sql.init.mode=never"
})
@EnabledIfSystemProperty(named = "planification.pg16.url", matches = ".+")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class LancementConcurrentPostgresContractIT {

    private final AtomicInteger demarrages = new AtomicInteger();
    @Autowired
    private JdbcTemplate jdbc;

    private static ParametresDOptimisation parametres() {
        return ParametresDOptimisation.parDefaut(Perimetre.PATIENTS, JeuDOptimisation.LUNDI);
    }

    @BeforeEach
    @AfterEach
    void vider() {
        SchemaDeLOptimisationH2.vider(jdbc, JeuDePlaces.CENTRE.valeur());
        demarrages.set(0);
    }

    private LancerUneOptimisation instance(Duration delaiDeVerrou, long attenteDansLaTransaction) {
        TransactionTemplate tx = new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
        JsonMapper mapper = JsonMapper.builder().build();
        DonneesDOptimisation donnees = new JeuDOptimisation().donnees();
        Optimiseur optimiseur = new Optimiseur() {
            @Override
            public void demarrer(ExecutionId execution, DonneesDOptimisation d, ParametresDOptimisation p, Ecouteur ecouteur) {
                demarrages.incrementAndGet();
            }

            @Override
            public boolean arreter(ExecutionId execution) {
                return false;
            }
        };
        NouveauxIdentifiantsDeLOptimisation ids = new NouveauxIdentifiantsDeLOptimisationAleatoires();
        return new LancerUneOptimisation(new UniteDeTravailSpring(tx, jdbc, delaiDeVerrou), new VerrouDesPlacesJdbc(jdbc),
                new ReglagesJdbc(jdbc), new ExecutionsJdbc(jdbc, mapper, "instance-test"), (centre, du, au) -> {
            try {
                Thread.sleep(attenteDansLaTransaction);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return donnees;
        }, new CalendriersDesPropositionsJdbc(jdbc, mapper), optimiseur, ids, Clock.systemUTC());
    }

    @Test
    void la_base_est_bien_postgresql_16() {
        assertThat(jdbc.queryForObject("SELECT version()", String.class)).startsWith("PostgreSQL 16");
    }

    @Test
    void should_let_exactly_one_of_several_simultaneous_launches_through_and_refuse_the_others() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
        // la ligne du centre existe (committée) : sa création simultanée est une autre règle (la perdante reçoit 409, ADR 0059)
        tx.executeWithoutResult(s -> new VerrouDesPlacesJdbc(jdbc).prendre(JeuDePlaces.CENTRE));
        for (int tour = 0; tour < 4; tour++) {
            SchemaDeLOptimisationH2.vider(jdbc, JeuDePlaces.CENTRE.valeur());
            demarrages.set(0);
            CountDownLatch depart = new CountDownLatch(1);
            List<CompletableFuture<String>> lancements = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                LancerUneOptimisation instance = instance(Duration.ofSeconds(10), 150);
                lancements.add(CompletableFuture.supplyAsync(() -> {
                    try {
                        depart.await();
                        instance.executer(JeuDePlaces.CENTRE, parametres(), "utilisateur");
                        return "202";
                    } catch (RegleDePlanificationViolee e) {
                        return e.code();
                    } catch (EcritureConcurrente e) {
                        return "409";
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return "interrompu";
                    }
                }));
            }
            depart.countDown();
            List<String> issues = new ArrayList<>();
            for (var f : lancements) issues.add(f.get(30, TimeUnit.SECONDS));

            assertThat(issues).as("tour " + tour).containsOnlyOnce("202");
            assertThat(issues.stream().filter(i -> !i.equals("202"))).as("tour " + tour).containsOnly("OPTIMISATION_DEJA_EN_COURS");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM planification_optimisation WHERE center_id = ? AND statut = 'EN_COURS'",
                    Integer.class, JeuDePlaces.CENTRE.valeur())).as("tour " + tour).isEqualTo(1);
            assertThat(demarrages.get()).as("tour " + tour).isEqualTo(1);
        }
    }

    @Test
    void should_answer_409_when_the_centre_lock_is_held_elsewhere_longer_than_the_lock_timeout() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
        tx.executeWithoutResult(s -> new VerrouDesPlacesJdbc(jdbc).prendre(JeuDePlaces.CENTRE));
        CountDownLatch tenu = new CountDownLatch(1);
        CountDownLatch relache = new CountDownLatch(1);
        CompletableFuture<Void> autreInstance = CompletableFuture.runAsync(() -> tx.executeWithoutResult(s -> {
            new VerrouDesPlacesJdbc(jdbc).prendre(JeuDePlaces.CENTRE);
            tenu.countDown();
            try {
                relache.await(20, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));
        assertThat(tenu.await(10, TimeUnit.SECONDS)).isTrue();
        LancerUneOptimisation impatiente = instance(Duration.ofMillis(500), 0);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> impatiente.executer(JeuDePlaces.CENTRE, parametres(), "utilisateur"))
                .isInstanceOf(EcritureConcurrente.class);

        relache.countDown();
        autreInstance.get(20, TimeUnit.SECONDS);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM planification_optimisation WHERE center_id = ?", Integer.class,
                JeuDePlaces.CENTRE.valeur())).isZero();
        ExecutionDOptimisation apres = instance(Duration.ofSeconds(5), 0).executer(JeuDePlaces.CENTRE, parametres(), "utilisateur");
        assertThat(apres.enCours()).isTrue();
    }
}
