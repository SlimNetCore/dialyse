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
 * Contrat des séances exécuté contre les ACL JDBC sur H2 (mode PostgreSQL). Les tables des autres modules sont créées ici avec
 * les colonnes que la Facturation lit ou écrit, dont les deux colonnes de la migration V39. Variante PostgreSQL 16 :
 * {@code SeancesPostgresContractIT}.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:facturation-contrat-seances;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SeancesJdbcContratTest extends SeancesJdbcContrat {

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void creerLesTablesEtViderLesLignes() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS seances (id UUID PRIMARY KEY, center_id UUID NOT NULL, patient_id UUID NOT NULL, "
                + "date_seance DATE NOT NULL, statut VARCHAR(255) NOT NULL, facture_id UUID, forfait_override_id UUID, "
                + "forfait_override_nom VARCHAR(255), forfait_override_prix NUMERIC(14, 2), "
                + "forfait_override_updated_at TIMESTAMP WITH TIME ZONE, forfait_override_updated_by VARCHAR(100), "
                + "statut_avant_exclusion VARCHAR(20), exclusion_absence_id UUID)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS patients (id UUID PRIMARY KEY, center_id UUID NOT NULL, code_patient VARCHAR(255), "
                + "nom VARCHAR(255) NOT NULL, prenom VARCHAR(255) NOT NULL, sexe VARCHAR(255) NOT NULL, date_admission DATE NOT NULL, "
                + "numero_assurance VARCHAR(255) NOT NULL, type_patient VARCHAR(255) NOT NULL, etat_patient VARCHAR(255))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS forfait (id UUID PRIMARY KEY, center_id UUID NOT NULL, code VARCHAR(50) NOT NULL, "
                + "libelle VARCHAR(255) NOT NULL, prix NUMERIC(10, 2))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS prise_en_charge (id UUID PRIMARY KEY, center_id UUID NOT NULL, "
                + "patient_id UUID NOT NULL, date_debut_demande DATE, date_fin_demande DATE, date_debut_effectif DATE, "
                + "date_fin_effectif DATE, forfait_demande_id UUID, forfait_effectif_id UUID, statut VARCHAR(255) NOT NULL, "
                + "created_at TIMESTAMP WITH TIME ZONE)");
        viderLesLignesDeTest();
    }

    @Override
    protected JdbcTemplate jdbc() {
        return jdbc;
    }
}
