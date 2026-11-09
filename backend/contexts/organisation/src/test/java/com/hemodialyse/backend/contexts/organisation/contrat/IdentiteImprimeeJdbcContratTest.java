package com.hemodialyse.backend.contexts.organisation.contrat;

import com.hemodialyse.backend.contexts.organisation.infrastructure.persistence.IdentiteImprimeeJdbc;
import com.hemodialyse.backend.contexts.organisation.publishedlanguage.IdentiteImprimee;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** L'adapter JDBC de l'identité imprimée passe le même contrat que le faux en mémoire (H2, mode PostgreSQL). */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:organisation-identite;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class IdentiteImprimeeJdbcContratTest extends IdentiteImprimeeContrat {

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void creerLesTablesEtVider() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS societes (id UUID PRIMARY KEY, code VARCHAR(30) NOT NULL, raison_sociale VARCHAR(200) NOT NULL, "
                + "adresse VARCHAR(255), ville VARCHAR(100), wilaya VARCHAR(100), telephone VARCHAR(50), email VARCHAR(150), "
                + "site_web VARCHAR(255), nif VARCHAR(50), nis VARCHAR(50), rc VARCHAR(50), pied_page VARCHAR(500), logo BYTEA, "
                + "actif BOOLEAN NOT NULL DEFAULT TRUE, created_at TIMESTAMP WITH TIME ZONE)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS centers (id UUID PRIMARY KEY, code VARCHAR(255), name VARCHAR(255), societe_id UUID NOT NULL, "
                + "adresse VARCHAR(255), ville VARCHAR(100), wilaya VARCHAR(100), telephone VARCHAR(50), email VARCHAR(150), "
                + "site_web VARCHAR(255), actif BOOLEAN)");
        jdbc.update("DELETE FROM centers WHERE code LIKE 'IDENT-%'");
        jdbc.update("DELETE FROM societes WHERE code LIKE 'IDENT-%'");
    }

    @Override
    protected IdentiteImprimee identite() {
        return new IdentiteImprimeeJdbc(jdbc);
    }

    @Override
    protected void societe(UUID id, boolean actif, Fiche f) {
        jdbc.update("INSERT INTO societes (id, code, raison_sociale, adresse, ville, wilaya, telephone, email, site_web, nif, nis, rc, "
                        + "pied_page, logo, actif) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                id, "IDENT-" + id.toString().substring(30), f.raisonSociale(), f.adresse(), f.ville(), f.wilaya(), f.telephone(),
                f.email(), f.siteWeb(), f.nif(), f.nis(), f.rc(), f.piedDePage(), f.logo(), actif);
    }

    @Override
    protected void centre(UUID id, UUID societe, String nom, String adresse, String ville, String wilaya, String telephone,
                          String email, String siteWeb, boolean actif) {
        jdbc.update("INSERT INTO centers (id, code, name, societe_id, adresse, ville, wilaya, telephone, email, site_web, actif) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                id, "IDENT-" + id.toString().substring(30), nom, societe, adresse, ville, wilaya, telephone, email, siteWeb, actif);
    }
}
