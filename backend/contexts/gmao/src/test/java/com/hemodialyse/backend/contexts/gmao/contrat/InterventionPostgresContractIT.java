package com.hemodialyse.backend.contexts.gmao.contrat;

import com.hemodialyse.backend.contexts.gmao.application.intervention.LectureInterventions;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.InterventionId;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.InterventionRepository;
import com.hemodialyse.backend.contexts.gmao.infrastructure.persistence.InterventionRepositoryJpa;
import com.hemodialyse.backend.contexts.gmao.infrastructure.persistence.InterventionSpringData;
import com.hemodialyse.backend.contexts.gmao.infrastructure.persistence.LectureInterventionsJpa;
import com.hemodialyse.backend.contexts.gmao.infrastructure.persistence.LigneDeCoutSpringData;
import com.hemodialyse.backend.contexts.gmao.infrastructure.persistence.RectificationSpringData;
import com.hemodialyse.backend.contexts.gmao.infrastructure.persistence.TacheSpringData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrat PostgreSQL 16 réel de la slice G4 : la MÊME suite {@link InterventionContrat} que le double en mémoire et
 * l'adapter sur H2, sur les tables historiques {@code gmao_interventions}, {@code gmao_lignes_cout_intervention} et
 * {@code gmao_rectifications_intervention} (V5, V7, V8 puis Hibernate) et {@code gmao_taches_intervention} (V36). C'est la base qui arrondit quantité et prix et qui
 * refuse les dépassements (KB-GMAO-44/45). Base JETABLE uniquement (voir backend/scripts/gmao-pg16-rejeu.sh) : le test vide
 * les interventions et leurs dépendants.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=${gmao.pg16.url}",
        "spring.datasource.username=${gmao.pg16.user:gmao_test}",
        "spring.datasource.password=${gmao.pg16.password:}",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.jpa.hibernate.ddl-auto=update",
        "spring.sql.init.mode=never"
})
@EnabledIfSystemProperty(named = "gmao.pg16.url", matches = ".+")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class InterventionPostgresContractIT extends InterventionContrat {

    @Autowired
    private InterventionSpringData interventions;
    @Autowired
    private LigneDeCoutSpringData lignes;
    @Autowired
    private RectificationSpringData rectifications;
    @Autowired
    private TacheSpringData taches;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

    private InterventionRepositoryJpa repository;
    private LectureInterventionsJpa lecture;

    @BeforeEach
    void creerLesAdaptersEtViderLesTables() {
        repository = new InterventionRepositoryJpa(interventions, lignes, rectifications, taches);
        lecture = new LectureInterventionsJpa(interventions, repository);
        jdbc.execute("TRUNCATE gmao_interventions, gmao_lignes_cout_intervention, gmao_rectifications_intervention, gmao_taches_intervention CASCADE");
        // le schéma Flyway réel porte les clés étrangères de l'intervention vers centers, app_user et gmao_equipements
        ReferentielPostgres.centre(jdbc, CENTRE_A.valeur());
        ReferentielPostgres.centre(jdbc, CENTRE_B.valeur());
        ReferentielPostgres.utilisateur(jdbc, ADMIN.valeur());
        ReferentielPostgres.equipement(jdbc, EQUIPEMENT_1.valeur(), CENTRE_A.valeur(), ADMIN.valeur());
        ReferentielPostgres.equipement(jdbc, EQUIPEMENT_2.valeur(), CENTRE_A.valeur(), ADMIN.valeur());
    }

    @Override
    protected InterventionRepository repository() {
        return repository;
    }

    @Override
    protected LectureInterventions lecture() {
        return lecture;
    }

    @org.junit.jupiter.api.Test
    void should_make_a_second_transaction_wait_for_the_lock_taken_to_load_for_modification() throws Exception {
        com.hemodialyse.backend.contexts.gmao.domain.intervention.Intervention intervention = planifier(CENTRE_A, EQUIPEMENT_1, T0);
        repository.enregistrer(intervention);

        VerrouDeLIntervention.verifier(id -> repository.chargerPourModification(id),
                new org.springframework.transaction.support.TransactionTemplate(transactionManager), intervention.id());
    }

    @Override
    protected void supprimerLogiquement(InterventionId id) {
        jdbc.update("UPDATE gmao_interventions SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?", id.valeur());
    }

    @Test
    void should_run_on_a_real_postgresql_16() {
        assertThat(jdbc.queryForObject("SHOW server_version", String.class)).startsWith("16.");
    }

    @Test
    void should_keep_the_historical_numeric_scales_of_the_cost_lines() {
        assertThat(jdbc.queryForList("SELECT column_name || '=' || numeric_precision || ',' || numeric_scale "
                + "FROM information_schema.columns WHERE table_name = 'gmao_lignes_cout_intervention' "
                + "AND column_name IN ('quantite', 'prix_unitaire')", String.class))
                .containsExactlyInAnyOrder("quantite=12,3", "prix_unitaire=12,2");
    }

    @Test
    void should_map_the_columns_the_module_writes() {
        assertThat(jdbc.queryForList("SELECT column_name FROM information_schema.columns "
                + "WHERE table_name = 'gmao_interventions'", String.class)).contains("intervenant_id", "etat_equipement_avant",
                "etat_equipement_apres", "priorite", "echeance", "symptome", "cause", "demarre_par", "demarre_le", "cloture_par",
                "cloture_le", "annule_par", "annule_le", "deleted_at");
    }
}
