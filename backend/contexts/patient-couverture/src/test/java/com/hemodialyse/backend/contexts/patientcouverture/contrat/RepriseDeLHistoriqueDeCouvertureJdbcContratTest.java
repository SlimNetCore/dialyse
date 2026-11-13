package com.hemodialyse.backend.contexts.patientcouverture.contrat;

import com.hemodialyse.backend.contexts.patientcouverture.application.ReprendreLHistoriqueDeCouverture;
import com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence.CouvertureDeLaRepriseJdbc;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.RepriseDeLHistoriqueDeCouverture;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.RepriseDeLHistoriqueDeCouverture.ReportDUneAttestation;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.RepriseDeLHistoriqueDeCouverture.ReportDUnePriseEnCharge;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Le même contrat sur les tables réelles (schéma de test H2 en mode PostgreSQL, jamais une base partagée). */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:patient-couverture-reprise-couverture;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RepriseDeLHistoriqueDeCouvertureJdbcContratTest extends RepriseDeLHistoriqueDeCouvertureContrat {

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void creerLesTablesEtViderLesLignes() {
        PatientsJdbcContratTest.creerLesTables(jdbc);
        vider();
    }

    @AfterEach
    void viderApres() {
        vider();
    }

    private void vider() {
        for (UUID centre : new UUID[]{CENTRE, AUTRE}) {
            jdbc.update("DELETE FROM prise_en_charge WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM attestation_droit WHERE center_id = ?", centre);
        }
    }

    @Override
    protected RepriseDeLHistoriqueDeCouverture reprise() {
        return new ReprendreLHistoriqueDeCouverture(new CouvertureDeLaRepriseJdbc(jdbc));
    }

    private static LocalDate date(java.sql.Date d) {
        return d == null ? null : d.toLocalDate();
    }

    @Override
    protected ReportDUneAttestation attestationEnBase(UUID id) {
        List<ReportDUneAttestation> lignes = jdbc.query("SELECT date_debut, date_fin FROM attestation_droit WHERE id = ?",
                (rs, n) -> new ReportDUneAttestation(date(rs.getDate("date_debut")), date(rs.getDate("date_fin"))), id);
        return lignes.isEmpty() ? null : lignes.getFirst();
    }

    @Override
    protected ReportDUnePriseEnCharge priseEnChargeEnBase(UUID id) {
        List<ReportDUnePriseEnCharge> lignes = jdbc.query("SELECT date_debut_demande, date_fin_demande, forfait_demande_id, statut, "
                        + "date_debut_effectif, date_fin_effectif, forfait_effectif_id FROM prise_en_charge WHERE id = ?",
                (rs, n) -> new ReportDUnePriseEnCharge(date(rs.getDate("date_debut_demande")), date(rs.getDate("date_fin_demande")),
                        rs.getObject("forfait_demande_id", UUID.class), rs.getString("statut"), date(rs.getDate("date_debut_effectif")),
                        date(rs.getDate("date_fin_effectif")), rs.getObject("forfait_effectif_id", UUID.class)), id);
        return lignes.isEmpty() ? null : lignes.getFirst();
    }

    @Override
    protected boolean dateDeCreationPosee(UUID id) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM (SELECT created_at FROM attestation_droit WHERE id = ? "
                + "UNION ALL SELECT created_at FROM prise_en_charge WHERE id = ?) t WHERE created_at IS NOT NULL", Integer.class, id, id) == 1;
    }
}
