package com.hemodialyse.backend.contexts.facturation.contrat;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Contrat des lectures publiées exécuté contre l'adaptateur JDBC sur H2 (mode PostgreSQL), avec les tables du schéma figé. Variante PostgreSQL 16 :
 * {@code LecturesPubliquesPostgresContractIT}.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:facturation-contrat-lectures-publiques;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class LecturesPubliquesJdbcContratTest extends LecturesPubliquesJdbcContrat {

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void creerLesTablesEtViderLesLignes() {
        SchemaDesFacturesH2.creer(jdbc);
        viderLesLignesDeTest();
    }

    @Override
    protected JdbcTemplate jdbc() {
        return jdbc;
    }
}
