package com.hemodialyse.backend.contexts.organisation.contrat;

import com.hemodialyse.backend.contexts.organisation.infrastructure.persistence.LibellesDeLOrganisationJdbc;
import com.hemodialyse.backend.contexts.organisation.publishedlanguage.LibellesDeLOrganisation;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** L'adapter JDBC des libellés passe le même contrat que le faux en mémoire (H2, mode PostgreSQL). */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:organisation-libelles;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class LibellesJdbcContratTest extends LibellesContrat {

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void creerLesTablesEtVider() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS salle (id UUID PRIMARY KEY, center_id UUID NOT NULL, code VARCHAR(50) NOT NULL, "
                + "nom VARCHAR(255) NOT NULL)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS position_creneau (id UUID PRIMARY KEY, center_id UUID NOT NULL, "
                + "code VARCHAR(50) NOT NULL, libelle VARCHAR(255) NOT NULL)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS medecin (id UUID PRIMARY KEY, center_id UUID NOT NULL, nom VARCHAR(255) NOT NULL, "
                + "prenom VARCHAR(255), specialite VARCHAR(255), actif BOOLEAN NOT NULL DEFAULT TRUE)");
        jdbc.update("DELETE FROM salle");
        jdbc.update("DELETE FROM position_creneau");
        jdbc.update("DELETE FROM medecin");
    }

    @Override
    protected LibellesDeLOrganisation libelles() {
        return new LibellesDeLOrganisationJdbc(jdbc);
    }

    @Override
    protected void salle(UUID centre, UUID id, String code, String nom) {
        jdbc.update("INSERT INTO salle (id, center_id, code, nom) VALUES (?, ?, ?, ?)", id, centre, code, nom);
    }

    @Override
    protected void creneau(UUID centre, UUID id, String code, String libelle) {
        jdbc.update("INSERT INTO position_creneau (id, center_id, code, libelle) VALUES (?, ?, ?, ?)", id, centre, code, libelle);
    }

    @Override
    protected void medecin(UUID centre, UUID id, String nom, String prenom, boolean actif) {
        jdbc.update("INSERT INTO medecin (id, center_id, nom, prenom, actif) VALUES (?, ?, ?, ?, ?)", id, centre, nom, prenom, actif);
    }
}
