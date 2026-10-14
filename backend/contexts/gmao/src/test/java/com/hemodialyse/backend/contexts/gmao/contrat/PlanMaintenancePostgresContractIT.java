package com.hemodialyse.backend.contexts.gmao.contrat;

import com.hemodialyse.backend.contexts.gmao.application.planmaintenance.LecturePlans;
import com.hemodialyse.backend.contexts.gmao.domain.planmaintenance.PlanMaintenanceId;
import com.hemodialyse.backend.contexts.gmao.domain.planmaintenance.PlanMaintenanceRepository;
import com.hemodialyse.backend.contexts.gmao.infrastructure.persistence.LecturePlansJpa;
import com.hemodialyse.backend.contexts.gmao.infrastructure.persistence.PlanMaintenanceRepositoryJpa;
import com.hemodialyse.backend.contexts.gmao.infrastructure.persistence.PlanMaintenanceSpringData;
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

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrat PostgreSQL 16 réel : la MÊME suite {@link PlanMaintenanceContrat} que le double en mémoire et l'adapter sur H2,
 * exécutée sur la table historique {@code gmao_plans_maintenance} telle que la production la porte : chaîne Flyway
 * puis {@code ddl-auto=update} (KB-21 : la colonne lue est {@code tachesaeffectuer}, créée par Hibernate).
 *
 * <p>Base JETABLE uniquement (voir backend/scripts/gmao-pg16-rejeu.sh) : le test vide {@code gmao_plans_maintenance}.
 * Activé par {@code -Dgmao.pg16.url=jdbc:postgresql://…} ({@code -Dgmao.pg16.user}, {@code -Dgmao.pg16.password}).
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
class PlanMaintenancePostgresContractIT extends PlanMaintenanceContrat {

    @Autowired
    private PlanMaintenanceSpringData donnees;
    @Autowired
    private JdbcTemplate jdbc;

    private PlanMaintenanceRepository repository;
    private LecturePlans lecture;

    @BeforeEach
    void creerLesAdaptersEtViderLaTable() {
        repository = new PlanMaintenanceRepositoryJpa(donnees);
        lecture = new LecturePlansJpa(donnees);
        donnees.deleteAll();
        // le schéma Flyway réel porte les clés étrangères des plans vers centers, app_user et gmao_equipements
        ReferentielPostgres.centre(jdbc, CENTRE_A.valeur());
        ReferentielPostgres.centre(jdbc, CENTRE_B.valeur());
        ReferentielPostgres.utilisateur(jdbc, ADMIN.valeur());
        ReferentielPostgres.equipement(jdbc, EQUIPEMENT_1.valeur(), CENTRE_A.valeur(), ADMIN.valeur());
        ReferentielPostgres.equipement(jdbc, EQUIPEMENT_2.valeur(), CENTRE_A.valeur(), ADMIN.valeur());
    }

    @Override
    protected PlanMaintenanceRepository repository() {
        return repository;
    }

    @Override
    protected LecturePlans lecture() {
        return lecture;
    }

    @Override
    protected void supprimerLogiquement(PlanMaintenanceId id) {
        jdbc.update("UPDATE gmao_plans_maintenance SET deleted_at = ? WHERE id = ?",
                Timestamp.from(Instant.parse("2026-03-02T00:00:00Z")), id.valeur());
    }

    private Map<String, String> colonnes(String schema, String table) {
        return jdbc.queryForList("SELECT column_name, data_type, is_nullable FROM information_schema.columns "
                        + "WHERE table_schema = ? AND table_name = ?", schema, table).stream()
                .collect(Collectors.toMap(l -> (String) l.get("column_name"),
                        l -> l.get("data_type") + "/" + l.get("is_nullable")));
    }

    @Test
    void should_run_on_a_real_postgresql_16() {
        assertThat(jdbc.queryForObject("SHOW server_version", String.class)).startsWith("16.");
    }

    @Test
    void kb21_the_legacy_text_column_is_tachesaeffectuer() {
        assertThat(colonnes("public", "gmao_plans_maintenance")).containsKey("tachesaeffectuer");
    }

    @Test
    void should_store_the_text_of_the_tasks_in_that_column() {
        var plan = nouveauPlan(CENTRE_A, EQUIPEMENT_1, "Tâches", PREVUE);

        assertThat(jdbc.queryForObject("SELECT tachesaeffectuer FROM gmao_plans_maintenance WHERE id = ?", String.class,
                plan.id().valeur())).isEqualTo(plan.tachesAEffectuer());
    }

    @Test
    void should_have_the_modulith_event_publication_registry_created_by_flyway_V35() {
        assertThat(colonnes("public", "event_publication")).containsOnlyKeys("id", "listener_id", "event_type",
                "serialized_event", "publication_date", "completion_date", "status", "completion_attempts",
                "last_resubmission_date");
    }
}
