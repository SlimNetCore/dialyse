package com.hemodialyse.backend.contexts.patientcouverture.contrat;

import com.hemodialyse.backend.contexts.patientcouverture.application.ListeDesPatientsAImprimer;
import com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence.ListeDesPatientsAImprimerJdbc;
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

/** Contrat de la liste des patients à imprimer sur la table réelle (H2, mode PostgreSQL) : le même jeu que le double en mémoire. */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:patient-couverture-liste-a-imprimer;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ListeDesPatientsAImprimerJdbcContratTest extends ListeDesPatientsAImprimerContrat {

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
        jdbc.update("DELETE FROM patients WHERE center_id IN (?, ?)", CENTRE.valeur(), AUTRE.valeur());
    }

    @Override
    protected ListeDesPatientsAImprimer liste() {
        return new ListeDesPatientsAImprimerJdbc(jdbc);
    }

    @Override
    protected void poser(CentreId centre, ListeDesPatientsAImprimer.PatientImprime p) {
        jdbc.update("INSERT INTO patients (id, center_id, code_patient, nom, prenom, sexe, groupe_sanguin, date_naissance, date_admission, "
                        + "numero_assurance, tel_mobile, etat_patient, type_patient) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)", UUID.randomUUID(),
                centre.valeur(), p.codePatient(), p.nom(), p.prenom(), p.sexe(), p.groupeSanguin(),
                p.dateNaissance() == null ? null : Date.valueOf(p.dateNaissance()), Date.valueOf(p.dateAdmission()), p.numeroAssurance(),
                p.telMobile(), p.etatPatient(), "PERMANENT");
    }
}
