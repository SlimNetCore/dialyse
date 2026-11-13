package com.hemodialyse.backend.contexts.documents.contrat;

import com.hemodialyse.backend.contexts.documents.application.GabaritPersonnaliseDuCentre;
import com.hemodialyse.backend.contexts.documents.domain.HistoriqueDesVersionsRepository;
import com.hemodialyse.backend.contexts.documents.infrastructure.persistence.HistoriqueDesVersionsJdbc;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** L'adapter JDBC sur H2 (mode PostgreSQL), table calquée sur le DDL de {@code schema.sql} (= Flyway V1) : le même contrat que le double. */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:documents-versions;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class HistoriqueDesVersionsJdbcContratTest extends HistoriqueDesVersionsContrat {

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void avant() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS modele_document_version (id UUID PRIMARY KEY, actif BOOLEAN NOT NULL, center_id UUID NOT NULL, "
                + "commentaire VARCHAR(500), contenu TEXT NOT NULL, modele_id UUID NOT NULL, sha256 VARCHAR(64) NOT NULL, "
                + "taille_octets INTEGER NOT NULL, uploaded_at TIMESTAMP(6) WITH TIME ZONE NOT NULL, uploaded_by VARCHAR(100) NOT NULL, "
                + "version INTEGER NOT NULL, CONSTRAINT uk_modele_version UNIQUE (modele_id, version))");
        vider();
    }

    @AfterEach
    void apres() {
        vider();
    }

    private void vider() {
        jdbc.update("DELETE FROM modele_document_version WHERE center_id IN (?, ?)", D, E);
    }

    @Override
    protected HistoriqueDesVersionsRepository historiques() {
        return new HistoriqueDesVersionsJdbc(jdbc);
    }

    @Override
    protected GabaritPersonnaliseDuCentre pourLeMoteur() {
        return new HistoriqueDesVersionsJdbc(jdbc);
    }
}
