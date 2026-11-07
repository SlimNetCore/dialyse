package com.hemodialyse.backend.contexts.dossiermedical.contrat;

import com.hemodialyse.backend.contexts.dossiermedical.domain.Alertes;
import com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence.AlertesJdbc;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Contrat des alertes d'observance sur H2 (mode PostgreSQL). Variante PostgreSQL 16 : {@code AlertesPostgresContractIT}. */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:dossier-medical-alertes;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AlertesJdbcContratTest extends AlertesContrat {

    @Autowired
    private JdbcTemplate jdbc;

    /** Schéma H2 de la table : colonnes de V1 et les 4 colonnes de détail de la migration V53 (KB-MED-33). */
    static void creerLaTable(JdbcTemplate jdbc) {
        jdbc.execute("CREATE TABLE IF NOT EXISTS alertes_observance (id UUID PRIMARY KEY, center_id UUID NOT NULL, "
                + "created_at TIMESTAMP WITH TIME ZONE NOT NULL, doses_administrees INTEGER NOT NULL, doses_attendues INTEGER NOT NULL, "
                + "message VARCHAR(500), patient_id UUID NOT NULL, periode_debut DATE NOT NULL, periode_fin DATE NOT NULL, "
                + "resolved_at TIMESTAMP WITH TIME ZONE, type_alerte VARCHAR(20) NOT NULL, type_traitement VARCHAR(20) NOT NULL, "
                + "unite_dose VARCHAR(10), dose_prescrite INTEGER, frequence_valeur INTEGER, frequence_unite VARCHAR(10))");
    }

    static void vider(JdbcTemplate jdbc) {
        for (Object centre : new Object[]{CENTRE_A.valeur(), CENTRE_B.valeur()}) {
            jdbc.update("DELETE FROM alertes_observance WHERE center_id = ?", centre);
        }
    }

    @BeforeEach
    void avant() {
        creerLaTable(jdbc);
        vider(jdbc);
    }

    @AfterEach
    void apres() {
        vider(jdbc);
    }

    @Override
    protected Alertes alertes() {
        return new AlertesJdbc(jdbc);
    }
}
