package com.hemodialyse.backend.contexts.gmao.contrat;

import com.hemodialyse.backend.contexts.gmao.application.piecejointe.LecturePiecesJointes;
import com.hemodialyse.backend.contexts.gmao.domain.piecejointe.PieceJointeRepository;
import com.hemodialyse.backend.contexts.gmao.infrastructure.persistence.LecturePiecesJointesJpa;
import com.hemodialyse.backend.contexts.gmao.infrastructure.persistence.PieceJointeRepositoryJpa;
import com.hemodialyse.backend.contexts.gmao.infrastructure.persistence.PieceJointeSpringData;
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
 * Contrat PostgreSQL 16 réel de la slice G5 : la MÊME suite {@link PieceJointeContrat} que le double en mémoire et l'adapter
 * sur H2, sur la table historique {@code gmao_documents_intervention} (V7, sans clé étrangère) dont la colonne {@code contenu} est
 * un vrai {@code bytea}. Base JETABLE uniquement (voir backend/scripts/gmao-pg16-rejeu.sh) : le test vide la table.
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
class PieceJointePostgresContractIT extends PieceJointeContrat {

    @Autowired
    private PieceJointeSpringData donnees;
    @Autowired
    private JdbcTemplate jdbc;

    private PieceJointeRepositoryJpa repository;
    private LecturePiecesJointesJpa lecture;

    @BeforeEach
    void creerLesAdaptersEtViderLaTable() {
        repository = new PieceJointeRepositoryJpa(donnees);
        lecture = new LecturePiecesJointesJpa(donnees);
        jdbc.execute("TRUNCATE gmao_documents_intervention");
    }

    @Override
    protected PieceJointeRepository repository() {
        return repository;
    }

    @Override
    protected LecturePiecesJointes lecture() {
        return lecture;
    }

    @Test
    void should_run_on_a_real_postgresql_16() {
        assertThat(jdbc.queryForObject("SHOW server_version", String.class)).startsWith("16.");
    }

    @Test
    void should_keep_the_bytes_in_a_real_bytea_column_and_the_historical_columns() {
        assertThat(jdbc.queryForObject("SELECT data_type FROM information_schema.columns "
                + "WHERE table_name = 'gmao_documents_intervention' AND column_name = 'contenu'", String.class)).isEqualTo("bytea");
        assertThat(jdbc.queryForList("SELECT column_name FROM information_schema.columns "
                + "WHERE table_name = 'gmao_documents_intervention'", String.class)).containsExactlyInAnyOrder("id",
                "intervention_id", "centre_id", "type", "nom", "content_type", "taille", "contenu", "ajoute_par", "ajoute_le");
    }
}
