package com.hemodialyse.backend.contexts.dossiermedical.contrat;

import com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence.DonneesMedicalesDeLaRepriseJdbc;
import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.DonneesMedicalesDeLaReprise;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Contrat des données médicales d'un lot de reprise sur H2 (mode PostgreSQL). Les douze tables sont créées avec les seules colonnes lues
 * ({@code id}, {@code center_id}, {@code patient_id}) : les noms réels de colonnes sont ceux de la plateforme, repris mot pour mot.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:dossier-medical-reprise;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DonneesMedicalesDeLaRepriseJdbcContratTest extends DonneesMedicalesDeLaRepriseContrat {

    @Autowired
    private JdbcTemplate jdbc;

    private static List<String> tables() {
        List<String> natures = new ArrayList<>(DonneesMedicalesDeLaReprise.NATURES_CONTROLEES_AVANT_LES_BONS_DE_SORTIE);
        natures.addAll(DonneesMedicalesDeLaReprise.NATURES_CONTROLEES_APRES_LES_BONS_DE_SORTIE);
        return natures;
    }

    @BeforeEach
    void avant() {
        for (String table : tables()) {
            jdbc.execute("CREATE TABLE IF NOT EXISTS " + table + " (id UUID PRIMARY KEY, center_id UUID NOT NULL, patient_id UUID NOT NULL)");
        }
        vider();
    }

    @AfterEach
    void apres() {
        vider();
    }

    private void vider() {
        for (String table : tables()) {
            jdbc.update("DELETE FROM " + table);
        }
    }

    @Override
    protected DonneesMedicalesDeLaReprise reprise() {
        return new DonneesMedicalesDeLaRepriseJdbc(jdbc);
    }

    @Override
    protected void donnee(String nature, UUID id, UUID centre, UUID patient) {
        jdbc.update("INSERT INTO " + nature + " (id, center_id, patient_id) VALUES (?, ?, ?)", id, centre, patient);
    }

    @Override
    protected boolean existe(String nature, UUID id) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + nature + " WHERE id = ?", Integer.class, id) > 0;
    }
}
