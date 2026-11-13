package com.hemodialyse.backend.contexts.patientcouverture.contrat;

import com.hemodialyse.backend.contexts.patientcouverture.application.AnnulerLaReprise;
import com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence.DonneesDeLaRepriseJdbc;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.AnnulationDeLaReprise;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Le même contrat sur les tables réelles (schéma de test H2 en mode PostgreSQL, jamais une base partagée). */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:patient-couverture-annulation;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AnnulationDeLaRepriseJdbcContratTest extends AnnulationDeLaRepriseContrat {

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
            jdbc.update("DELETE FROM assure_patient WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM patients WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM assure WHERE center_id = ?", centre);
        }
    }

    @Override
    protected AnnulationDeLaReprise annulation() {
        return new AnnulerLaReprise(new DonneesDeLaRepriseJdbc(jdbc));
    }

    @Override
    protected void patient(UUID centre, UUID id, String numeroAssuranceDeLAssure) {
        jdbc.update("INSERT INTO patients (id, center_id, nom, prenom, sexe, date_admission, numero_assurance, type_patient, "
                + "assure_numero_assurance) VALUES (?, ?, 'N', 'P', 'M', DATE '2020-01-01', ?, 'NON_VACANCIER', ?)", id, centre,
                "N-" + id, numeroAssuranceDeLAssure);
    }

    @Override
    protected void assure(UUID centre, String numero) {
        jdbc.update("INSERT INTO assure (numero_assurance, center_id, nom) VALUES (?, ?, 'N')", numero, centre);
    }

    @Override
    protected void affectation(UUID centre, UUID id, UUID patient, String numero) {
        jdbc.update("INSERT INTO assure_patient (id, patient_id, numero_assurance, center_id, is_primary, date_affectation) "
                + "VALUES (?, ?, ?, ?, true, CURRENT_TIMESTAMP)", id, patient, numero, centre);
    }

    @Override
    protected void priseEnCharge(UUID id, UUID patient) {
        jdbc.update("INSERT INTO prise_en_charge (id, patient_id, center_id, statut) VALUES (?, ?, ?, 'VALIDEE')", id, patient, CENTRE);
    }

    @Override
    protected void attestation(UUID id, UUID patient) {
        jdbc.update("INSERT INTO attestation_droit (id, patient_id, center_id) VALUES (?, ?, ?)", id, patient, CENTRE);
    }

    @Override
    protected boolean patientExiste(UUID id) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM patients WHERE id = ?", Integer.class, id) > 0;
    }

    @Override
    protected boolean assureExiste(String numero) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM assure WHERE numero_assurance = ?", Integer.class, numero) > 0;
    }

    @Override
    protected boolean affectationExiste(UUID id) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM assure_patient WHERE id = ?", Integer.class, id) > 0;
    }
}
