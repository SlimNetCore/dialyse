package com.hemodialyse.backend.contexts.facturation.contrat;

import com.hemodialyse.backend.contexts.comptabilite.publishedlanguage.FacturesEmises;
import com.hemodialyse.backend.contexts.comptabilite.publishedlanguage.ReglementsEncaisses;
import com.hemodialyse.backend.contexts.facturation.infrastructure.acl.comptabilite.FacturesEmisesJdbc;
import com.hemodialyse.backend.contexts.facturation.infrastructure.acl.comptabilite.ReglementsEncaissesJdbc;
import com.hemodialyse.backend.contexts.facturation.infrastructure.persistence.FacturationDuPatientJdbc;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.FacturationDuPatient;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Le contrat des lectures publiées exécuté contre les adaptateurs JDBC ; les variantes H2 et PostgreSQL 16 ne diffèrent que par
 * leur base. Les lignes de test sont supprimées avant chaque test.
 */
public abstract class LecturesPubliquesJdbcContrat extends LecturesPubliquesContrat {

    private int rang;

    protected abstract JdbcTemplate jdbc();

    protected void viderLesLignesDeTest() {
        rang = 0;
        SchemaDesFacturesH2.vider(jdbc(), CENTRE, AUTRE_CENTRE);
    }

    @Override
    protected FacturationDuPatient facturationDuPatient() {
        return new FacturationDuPatientJdbc(jdbc());
    }

    @Override
    protected FacturesEmises facturesEmises() {
        return new FacturesEmisesJdbc(jdbc());
    }

    @Override
    protected ReglementsEncaisses reglementsEncaisses() {
        return new ReglementsEncaissesJdbc(jdbc());
    }

    @Override
    protected void poserLaFacture(UUID id, UUID centre, UUID patient, String numero, String debut, String fin,
                                  String dateFacturation, String ht, String tva, String ttc, UUID payeur) {
        rang++;
        jdbc().update("INSERT INTO factures (id, center_id, patient_id, numero_facture, centre_payeur_id_snapshot, period_start, "
                        + "period_end, date_facturation, tva_rate, total_ht, total_tva, total_ttc) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?, ?)",
                id, centre, patient, numero, payeur, Date.valueOf(LocalDate.parse(debut)), Date.valueOf(LocalDate.parse(fin)),
                Date.valueOf(LocalDate.parse(dateFacturation)), new BigDecimal(ht), new BigDecimal(tva), new BigDecimal(ttc));
    }

    @Override
    protected void poserLeReglement(UUID id, UUID centre, UUID facture, String montant, String date, String mode, String auteur) {
        jdbc().update("INSERT INTO facture_reglements (id, facture_id, center_id, montant, date_reglement, mode_reglement, saisi_par) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)", id, facture, centre, new BigDecimal(montant),
                Date.valueOf(LocalDate.parse(date)), mode, auteur);
    }
}
