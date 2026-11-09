package com.hemodialyse.backend.contexts.organisation.contrat;

import com.hemodialyse.backend.contexts.organisation.infrastructure.persistence.AnnuaireDesCentresJdbc;
import com.hemodialyse.backend.contexts.organisation.infrastructure.persistence.AnnuaireDesSocietesJdbc;
import com.hemodialyse.backend.contexts.organisation.publishedlanguage.AnnuaireDesCentres;
import com.hemodialyse.backend.contexts.organisation.publishedlanguage.AnnuaireDesSocietes;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Les adapters JDBC des annuaires passent le même contrat que le faux en mémoire (H2, mode PostgreSQL). */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:organisation-annuaires;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AnnuairesJdbcContratTest extends AnnuairesContrat {

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void creerLesTablesEtVider() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS societes (id UUID PRIMARY KEY, code VARCHAR(30) NOT NULL, raison_sociale VARCHAR(200) NOT NULL, "
                + "actif BOOLEAN NOT NULL DEFAULT TRUE, created_at TIMESTAMP WITH TIME ZONE)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS centers (id UUID PRIMARY KEY, code VARCHAR(255), name VARCHAR(255), societe_id UUID NOT NULL, "
                + "fuseau_horaire VARCHAR(50), actif BOOLEAN)");
        jdbc.update("DELETE FROM centers WHERE code LIKE 'ANNU-%'");
        jdbc.update("DELETE FROM societes WHERE code LIKE 'ANNU-%'");
    }

    @Override
    protected AnnuaireDesCentres centres() {
        return new AnnuaireDesCentresJdbc(jdbc);
    }

    @Override
    protected AnnuaireDesSocietes societes() {
        return new AnnuaireDesSocietesJdbc(jdbc);
    }

    @Override
    protected void societe(UUID id, String raisonSociale, boolean actif) {
        jdbc.update("INSERT INTO societes (id, code, raison_sociale, actif) VALUES (?, ?, ?, ?)",
                id, "ANNU-" + id.toString().substring(30), raisonSociale, actif);
    }

    @Override
    protected void centre(UUID id, UUID societe, String nom, Boolean actif, String fuseau) {
        jdbc.update("INSERT INTO centers (id, code, name, societe_id, actif, fuseau_horaire) VALUES (?, ?, ?, ?, ?, ?)",
                id, "ANNU-" + id.toString().substring(30), nom, societe, actif, fuseau);
    }
}
