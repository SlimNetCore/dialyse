package com.hemodialyse.backend.contexts.patientcouverture.contrat;

import com.hemodialyse.backend.contexts.patientcouverture.application.PriseEnChargeAImprimer;
import com.hemodialyse.backend.contexts.patientcouverture.application.PriseEnChargeAImprimer.PriseEnChargeImprimable;
import com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence.PriseEnChargeAImprimerJdbc;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.LocalDate;
import java.util.UUID;

/** Contrat de la prise en charge à imprimer sur les tables réelles (H2, mode PostgreSQL) : le même jeu que le double en mémoire. */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:patient-couverture-pec-a-imprimer;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PriseEnChargeAImprimerJdbcContratTest extends PriseEnChargeAImprimerContrat {

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void avant() {
        PatientsJdbcContratTest.creerLesTables(jdbc);
        vider();
    }

    @AfterEach
    void apres() {
        vider();
    }

    private void vider() {
        jdbc.update("DELETE FROM prise_en_charge WHERE center_id IN (?, ?)", CENTRE.valeur(), AUTRE.valeur());
        jdbc.update("DELETE FROM patients WHERE center_id IN (?, ?)", CENTRE.valeur(), AUTRE.valeur());
    }

    @Override
    protected PriseEnChargeAImprimer prisesEnCharge() {
        return new PriseEnChargeAImprimerJdbc(jdbc);
    }

    private static Date date(LocalDate d) {
        return d == null ? null : Date.valueOf(d);
    }

    @Override
    protected void poser(CentreId centre, CentreId centreDuPatient, UUID patient, UUID pec, PriseEnChargeImprimable p) {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM patients WHERE id = ?", Integer.class, patient) == 0) {
            jdbc.update("INSERT INTO patients (id, center_id, code_patient, nom, prenom, sexe, date_naissance, date_admission, "
                            + "numero_assurance, type_patient) VALUES (?,?,?,?,?,?,?,?,?,?)", patient, centreDuPatient.valeur(),
                    "P-" + patient, p.nom(), p.prenom(), "F", date(p.dateNaissance()), Date.valueOf("2026-03-04"), p.numeroAssurance(),
                    "PERMANENT");
        }
        jdbc.update("INSERT INTO prise_en_charge (id, patient_id, center_id, date_debut_demande, date_fin_demande, statut, "
                        + "date_debut_effectif, date_fin_effectif) VALUES (?,?,?,?,?,?,?,?)", pec, patient, centre.valeur(),
                date(p.dateDebutDemande()), date(p.dateFinDemande()), p.statut(), date(p.dateDebutEffectif()), date(p.dateFinEffectif()));
    }
}
