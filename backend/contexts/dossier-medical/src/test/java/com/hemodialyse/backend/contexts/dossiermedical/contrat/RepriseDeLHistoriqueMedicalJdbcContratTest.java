package com.hemodialyse.backend.contexts.dossiermedical.contrat;

import com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence.RepriseDeLHistoriqueMedicalJdbc;
import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.RepriseDeLHistoriqueMedical;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Contrat de l'écriture de l'historique médical d'un lot de reprise sur H2 (mode PostgreSQL). Les cinq tables sont créées avec les seules
 * colonnes que le contrat écrit (les noms de colonnes sont ceux de la plateforme, repris mot pour mot) ; les colonnes de dates de création et
 * de modification sont toujours présentes (nullables) pour vérifier que les abords vasculaires ne reçoivent pas de date de modification.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:dossier-medical-reprise-historique;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RepriseDeLHistoriqueMedicalJdbcContratTest extends RepriseDeLHistoriqueMedicalContrat {

    private static final Map<String, String> COLONNES = Map.of(
            RepriseDeLHistoriqueMedical.DOSSIER_DE_BASE, "date_mise_en_dialyse DATE, nephropathie_initiale VARCHAR(255)",
            RepriseDeLHistoriqueMedical.ANTECEDENTS, "type_antecedent VARCHAR(20), libelle_libre VARCHAR(255), date_debut DATE, statut_clinique VARCHAR(20)",
            RepriseDeLHistoriqueMedical.SEROLOGIES, "marqueur VARCHAR(15), resultat VARCHAR(15), date_prelevement DATE",
            RepriseDeLHistoriqueMedical.ABORDS_VASCULAIRES, "type_abord VARCHAR(20), date_creation DATE, actif BOOLEAN",
            RepriseDeLHistoriqueMedical.ANALYSES, "date_prelevement DATE, hb_g_dl NUMERIC(38, 2)");

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void avant() {
        COLONNES.forEach((table, colonnes) -> jdbc.execute("CREATE TABLE IF NOT EXISTS " + table + " (id UUID PRIMARY KEY, center_id UUID NOT NULL, "
                + "patient_id UUID NOT NULL, " + colonnes + ", created_at TIMESTAMP WITH TIME ZONE, updated_at TIMESTAMP WITH TIME ZONE)"));
        vider();
    }

    @AfterEach
    void apres() {
        vider();
    }

    private void vider() {
        COLONNES.keySet().forEach(table -> jdbc.update("DELETE FROM " + table));
    }

    @Override
    protected RepriseDeLHistoriqueMedical reprise() {
        return new RepriseDeLHistoriqueMedicalJdbc(jdbc);
    }

    @Override
    protected String valeur(String nature, UUID id, String champ) {
        String colonne = champ.replaceAll("([A-Z])", "_$1").toLowerCase(java.util.Locale.ROOT);
        Object v = jdbc.queryForObject("SELECT " + colonne + " FROM " + nature + " WHERE id = ?", Object.class, id);
        return v == null ? null : String.valueOf(v);
    }

    @Override
    protected boolean horodate(String nature, UUID id, String colonne) {
        return jdbc.queryForObject("SELECT " + colonne + " IS NOT NULL FROM " + nature + " WHERE id = ?", Boolean.class, id);
    }
}
