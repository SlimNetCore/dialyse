package com.hemodialyse.backend.contexts.patientcouverture.contrat;

import com.hemodialyse.backend.contexts.patientcouverture.application.ListeDesPrisesEnChargeAImprimer;
import com.hemodialyse.backend.contexts.patientcouverture.application.ListeDesPrisesEnChargeAImprimer.PriseEnChargeImprimee;
import com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence.ListeDesPrisesEnChargeAImprimerJdbc;
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
import java.util.UUID;

/** Contrat de la liste des prises en charge à imprimer sur les tables réelles (H2, mode PostgreSQL) : le même jeu que le double en mémoire. */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:patient-couverture-pec-a-imprimer;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ListeDesPrisesEnChargeAImprimerJdbcContratTest extends ListeDesPrisesEnChargeAImprimerContrat {

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
    protected ListeDesPrisesEnChargeAImprimer liste() {
        return new ListeDesPrisesEnChargeAImprimerJdbc(jdbc);
    }

    @Override
    protected void poser(CentreId centre, PriseEnChargeImprimee p) {
        poserPourUnPatientDuCentre(centre, centre, p);
    }

    @Override
    protected void poserPourUnPatientDuCentre(CentreId centre, CentreId centreDuPatient, PriseEnChargeImprimee p) {
        UUID patient = UUID.randomUUID();
        jdbc.update("INSERT INTO patients (id, center_id, code_patient, nom, prenom, sexe, date_admission, numero_assurance, type_patient) "
                        + "VALUES (?,?,?,?,?,?,?,?,?)", patient, centreDuPatient.valeur(), p.codePatient(), p.nom(), p.prenom(), "F",
                Date.valueOf("2026-03-04"), p.numeroAssurance(), "PERMANENT");
        jdbc.update("INSERT INTO prise_en_charge (id, patient_id, center_id, date_debut_demande, date_fin_demande, statut) "
                        + "VALUES (?,?,?,?,?,?)", UUID.randomUUID(), patient, centre.valeur(),
                p.dateDebutDemande() == null ? null : Date.valueOf(p.dateDebutDemande()),
                p.dateFinDemande() == null ? null : Date.valueOf(p.dateFinDemande()), p.statut());
    }
}
