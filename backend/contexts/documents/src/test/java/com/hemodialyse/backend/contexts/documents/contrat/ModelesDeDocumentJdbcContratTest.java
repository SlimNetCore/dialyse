package com.hemodialyse.backend.contexts.documents.contrat;

import com.hemodialyse.backend.contexts.documents.application.ModelesDeDocument;
import com.hemodialyse.backend.contexts.documents.infrastructure.persistence.ModelesDeDocumentJdbc;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

/** L'adapter JDBC sur H2 (mode PostgreSQL), table calquée sur V1 : le même contrat que le double en mémoire. */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:documents-modeles;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ModelesDeDocumentJdbcContratTest extends ModelesDeDocumentContrat {

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void avant() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS modele_document (id UUID PRIMARY KEY, center_id UUID NOT NULL, code VARCHAR(50) NOT NULL, "
                + "libelle VARCHAR(255) NOT NULL, type_document VARCHAR(50) NOT NULL, chemin_jrxml VARCHAR(500) NOT NULL, "
                + "format_impression VARCHAR(20) DEFAULT 'PDF' NOT NULL, description VARCHAR(500), active BOOLEAN DEFAULT TRUE NOT NULL, "
                + "created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL)");
        vider();
    }

    @AfterEach
    void apres() {
        vider();
    }

    private void vider() {
        jdbc.update("DELETE FROM modele_document WHERE center_id IN (?, ?)", D, E);
    }

    @Override
    protected ModelesDeDocument modeles() {
        return new ModelesDeDocumentJdbc(jdbc);
    }

    @Override
    protected void modele(UUID id, UUID centre, String type, String chemin, String format, boolean actif, Instant creeLe) {
        jdbc.update("INSERT INTO modele_document (id, center_id, code, libelle, type_document, chemin_jrxml, format_impression, active, created_at) "
                        + "VALUES (?,?,?,?,?,?,?,?,?)", id, centre, "C-" + id.toString().substring(30), "Modele", type, chemin, format, actif,
                Timestamp.from(creeLe));
    }
}
