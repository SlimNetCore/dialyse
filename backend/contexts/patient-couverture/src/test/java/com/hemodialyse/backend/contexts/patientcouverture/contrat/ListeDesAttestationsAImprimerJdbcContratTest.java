package com.hemodialyse.backend.contexts.patientcouverture.contrat;

import com.hemodialyse.backend.contexts.patientcouverture.application.ListeDesAttestationsAImprimer;
import com.hemodialyse.backend.contexts.patientcouverture.application.ListeDesAttestationsAImprimer.AttestationImprimee;
import com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence.ListeDesAttestationsAImprimerJdbc;
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

/** Contrat de la liste des ouvertures de droit à imprimer sur les tables réelles (H2, mode PostgreSQL) : le même jeu que le double en mémoire. */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:patient-couverture-attestations-a-imprimer;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ListeDesAttestationsAImprimerJdbcContratTest extends ListeDesAttestationsAImprimerContrat {

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
        jdbc.update("DELETE FROM attestation_droit WHERE center_id IN (?, ?)", CENTRE.valeur(), AUTRE.valeur());
        jdbc.update("DELETE FROM patients WHERE center_id IN (?, ?)", CENTRE.valeur(), AUTRE.valeur());
    }

    @Override
    protected ListeDesAttestationsAImprimer liste() {
        return new ListeDesAttestationsAImprimerJdbc(jdbc);
    }

    @Override
    protected void poser(CentreId centre, AttestationImprimee a) {
        poserPourUnPatientDuCentre(centre, centre, a);
    }

    @Override
    protected void poserPourUnPatientDuCentre(CentreId centre, CentreId centreDuPatient, AttestationImprimee a) {
        UUID patient = UUID.randomUUID();
        jdbc.update("INSERT INTO patients (id, center_id, code_patient, nom, prenom, sexe, date_admission, numero_assurance, type_patient) "
                        + "VALUES (?,?,?,?,?,?,?,?,?)", patient, centreDuPatient.valeur(), a.codePatient(), a.nom(), a.prenom(), "F",
                Date.valueOf("2026-03-04"), a.numeroAssurance(), "PERMANENT");
        jdbc.update("INSERT INTO attestation_droit (id, patient_id, center_id, date_debut, date_fin) VALUES (?,?,?,?,?)", UUID.randomUUID(),
                patient, centre.valeur(), Date.valueOf(a.dateDebut()), Date.valueOf(a.dateFin()));
    }
}
