package com.hemodialyse.backend.contexts.patientcouverture.contrat;

import com.hemodialyse.backend.contexts.patientcouverture.application.AttestationAImprimer;
import com.hemodialyse.backend.contexts.patientcouverture.application.AttestationAImprimer.AttestationImprimable;
import com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence.AttestationAImprimerJdbc;
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

/** Contrat de l'ouverture de droit à imprimer sur les tables réelles (H2, mode PostgreSQL) : le même jeu que le double en mémoire. */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:patient-couverture-attestation-a-imprimer;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AttestationAImprimerJdbcContratTest extends AttestationAImprimerContrat {

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
    protected AttestationAImprimer attestations() {
        return new AttestationAImprimerJdbc(jdbc);
    }

    @Override
    protected void poser(CentreId centre, CentreId centreDuPatient, UUID patient, UUID attestation, AttestationImprimable a) {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM patients WHERE id = ?", Integer.class, patient) == 0) {
            jdbc.update("INSERT INTO patients (id, center_id, code_patient, nom, prenom, sexe, date_naissance, date_admission, "
                            + "numero_assurance, adresse, tel_mobile, type_patient) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)", patient,
                    centreDuPatient.valeur(), "P-" + patient, a.nom(), a.prenom(), a.sexe(),
                    a.dateNaissance() == null ? null : Date.valueOf(a.dateNaissance()), Date.valueOf("2026-03-04"), a.numeroAssurance(),
                    a.adresse(), a.telephoneMobile(), "PERMANENT");
        }
        jdbc.update("INSERT INTO attestation_droit (id, patient_id, center_id, date_debut, date_fin) VALUES (?,?,?,?,?)", attestation,
                patient, centre.valeur(), Date.valueOf(a.dateDebut()), Date.valueOf(a.dateFin()));
    }
}
