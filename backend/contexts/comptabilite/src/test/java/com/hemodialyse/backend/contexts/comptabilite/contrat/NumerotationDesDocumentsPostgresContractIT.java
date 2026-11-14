package com.hemodialyse.backend.contexts.comptabilite.contrat;

import com.hemodialyse.backend.contexts.comptabilite.infrastructure.persistence.NumerotationDesDocumentsJdbc;
import com.hemodialyse.backend.contexts.comptabilite.publishedlanguage.NumerotationDesDocuments;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.UUID;

/**
 * Contrat PostgreSQL 16 REEL de la numérotation des documents (S-DDL2) : même jeu que le double et H2. Base JETABLE uniquement :
 * {@code -Dcomptabilite.pg16.url=jdbc:postgresql://...} (schéma Flyway déjà appliqué, table {@code app_settings} existante).
 */
@EnabledIfSystemProperty(named = "comptabilite.pg16.url", matches = ".+")
class NumerotationDesDocumentsPostgresContractIT extends NumerotationDesDocumentsContrat {

    private JdbcTemplate jdbc;

    @BeforeEach
    void connecter() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource(System.getProperty("comptabilite.pg16.url"),
                System.getProperty("comptabilite.pg16.user", "comptabilite_test"), System.getProperty("comptabilite.pg16.password", "")));
        nettoyer();
    }

    @AfterEach
    void nettoyer() {
        jdbc.update("DELETE FROM app_settings WHERE center_id IN (?, ?)", CENTRE_A, CENTRE_B);
    }

    @Override
    protected NumerotationDesDocuments numerotation() {
        return new NumerotationDesDocumentsJdbc(jdbc);
    }

    @Override
    protected void precharger(UUID centre, String type, String prefixe, long compteur) {
        jdbc.update("INSERT INTO app_settings (center_id, cle, prefixe, dernier_compteur) VALUES (?, ?, ?, ?)", centre, type, prefixe,
                compteur);
    }
}
