package com.hemodialyse.backend.contexts.gmao.contrat;

import com.hemodialyse.backend.contexts.gmao.application.intervenant.LectureIntervenants;
import com.hemodialyse.backend.contexts.gmao.domain.intervenant.IntervenantRepository;
import com.hemodialyse.backend.contexts.gmao.domain.intervenant.NatureIntervenant;
import com.hemodialyse.backend.contexts.gmao.infrastructure.persistence.IntervenantRepositoryJpa;
import com.hemodialyse.backend.contexts.gmao.infrastructure.persistence.IntervenantSpringData;
import com.hemodialyse.backend.contexts.gmao.infrastructure.persistence.LectureIntervenantsJpa;
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

import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrat PostgreSQL 16 réel de la slice G2 : la MÊME suite {@link IntervenantContrat} que le double en mémoire et
 * l'adapter sur H2, sur la table historique {@code gmao_intervenants} (produite par Hibernate, absente des migrations) :
 * c'est la base qui arrondit le tarif à deux décimales et refuse les dépassements (KB-GMAO-26/27). Base JETABLE
 * uniquement (voir backend/scripts/gmao-pg16-rejeu.sh) : le test vide {@code gmao_intervenants}.
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
class IntervenantPostgresContractIT extends IntervenantContrat {

    @Autowired
    private IntervenantSpringData donnees;
    @Autowired
    private JdbcTemplate jdbc;

    private IntervenantRepository repository;
    private LectureIntervenants lecture;

    @BeforeEach
    void creerLesAdaptersEtViderLaTable() {
        repository = new IntervenantRepositoryJpa(donnees);
        lecture = new LectureIntervenantsJpa(donnees);
        donnees.deleteAll();
    }

    @Override
    protected IntervenantRepository repository() {
        return repository;
    }

    @Override
    protected LectureIntervenants lecture() {
        return lecture;
    }

    private Map<String, String> colonnes(String schema, String table) {
        return jdbc.queryForList("SELECT column_name, data_type, is_nullable FROM information_schema.columns "
                        + "WHERE table_schema = ? AND table_name = ?", schema, table).stream()
                .collect(Collectors.toMap(l -> (String) l.get("column_name"), l -> l.get("data_type") + "/" + l.get("is_nullable")));
    }

    @Test
    void should_run_on_a_real_postgresql_16() {
        assertThat(jdbc.queryForObject("SHOW server_version", String.class)).startsWith("16.");
    }

    @Test
    void should_map_the_historical_columns_and_types() {
        assertThat(colonnes("public", "gmao_intervenants")).containsOnly(
                Map.entry("id", "uuid/NO"), Map.entry("centre_id", "uuid/NO"), Map.entry("nom", "character varying/NO"),
                Map.entry("type", "character varying/NO"), Map.entry("telephone", "character varying/YES"),
                Map.entry("email", "character varying/YES"), Map.entry("tarif_horaire_defaut", "numeric/YES"),
                Map.entry("actif", "boolean/NO"));
        assertThat(jdbc.queryForObject("SELECT numeric_precision || ',' || numeric_scale FROM information_schema.columns "
                + "WHERE table_name = 'gmao_intervenants' AND column_name = 'tarif_horaire_defaut'", String.class))
                .isEqualTo("10,2");
    }
}
