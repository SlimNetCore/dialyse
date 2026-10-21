package com.hemodialyse.backend.contexts.facturation.contrat;

import com.hemodialyse.backend.contexts.facturation.infrastructure.persistence.RepriseDesSoldesJdbc;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.RepriseDesSoldes;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Le contrat de la reprise des soldes exécuté contre l'adaptateur JDBC ; les variantes H2 et PostgreSQL 16 ne diffèrent que par
 * leur base. Les lignes de test sont supprimées avant chaque test.
 */
public abstract class RepriseDesSoldesJdbcContrat extends RepriseDesSoldesContrat {

    protected abstract JdbcTemplate jdbc();

    protected void viderLesLignesDeTest() {
        SchemaDesFacturesH2.vider(jdbc(), CENTRE, AUTRE_CENTRE);
    }

    @Override
    protected RepriseDesSoldes reprise() {
        return new RepriseDesSoldesJdbc(jdbc());
    }

    @Override
    protected void poserUnReglementDeLaPlateforme(UUID centre, UUID facture, String montant) {
        jdbc().update("INSERT INTO facture_reglements (id, facture_id, center_id, montant, date_reglement, saisi_par) "
                + "VALUES (?, ?, ?, ?, ?, 'caissier')", UUID.randomUUID(), facture, centre, new BigDecimal(montant),
                Date.valueOf(LocalDate.of(2026, 4, 1)));
    }

    @Override
    protected int lignes(UUID facture) {
        return jdbc().queryForObject("SELECT COUNT(*) FROM facture_lignes WHERE facture_id = ?", Integer.class, facture);
    }

    @Override
    protected int reglementsDeReprise(UUID facture) {
        return jdbc().queryForObject("SELECT COUNT(*) FROM facture_reglements WHERE facture_id = ? AND saisi_par = ?",
                Integer.class, facture, RepriseDesSoldes.AUTEUR_DE_LA_REPRISE);
    }

    @Override
    protected BigDecimal totalTtc(UUID facture) {
        return jdbc().queryForObject("SELECT total_ttc FROM factures WHERE id = ?", BigDecimal.class, facture);
    }
}
