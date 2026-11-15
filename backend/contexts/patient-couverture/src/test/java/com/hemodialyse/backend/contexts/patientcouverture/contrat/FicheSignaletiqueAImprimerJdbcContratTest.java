package com.hemodialyse.backend.contexts.patientcouverture.contrat;

import com.hemodialyse.backend.contexts.patientcouverture.application.FicheSignaletiqueAImprimer;
import com.hemodialyse.backend.contexts.patientcouverture.application.FicheSignaletiqueAImprimer.FicheImprimable;
import com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence.FicheSignaletiqueAImprimerJdbc;
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

/** Contrat de la fiche signalétique à imprimer sur la table réelle (H2, mode PostgreSQL) : le même jeu que le double en mémoire. */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:patient-couverture-fiche-a-imprimer;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class FicheSignaletiqueAImprimerJdbcContratTest extends FicheSignaletiqueAImprimerContrat {

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
    protected FicheSignaletiqueAImprimer fiches() {
        return new FicheSignaletiqueAImprimerJdbc(jdbc);
    }

    private static Date date(LocalDate d) {
        return d == null ? null : Date.valueOf(d);
    }

    @Override
    protected void poser(CentreId centre, UUID patient, FicheImprimable f) {
        jdbc.update("INSERT INTO patients (id, center_id, code_patient, civilite, nom, prenom, sexe, groupe_sanguin, date_naissance, "
                        + "lieu_naissance, date_admission, nombre_enfants, situation_familiale, profession1, adresse, tel_personnel, tel_mobile, "
                        + "tel_bureau, email, numero_assurance, etat_patient, qualite_assure, observation, sous_kt, assure_nom, assure_prenom, "
                        + "type_patient) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", patient, centre.valeur(),
                f.codePatient(), f.civilite(), f.nom(), f.prenom(), f.sexe(), f.groupeSanguin(), date(f.dateNaissance()), f.lieuNaissance(),
                date(f.dateAdmission()), f.nombreEnfants(), f.situationFamiliale(), f.profession(), f.adresse(), f.telPersonnel(),
                f.telMobile(), f.telBureau(), f.email(), f.numeroAssurance(), f.etatPatient(), f.qualiteAssure(), f.observation(), f.sousKt(),
                f.assureNom(), f.assurePrenom(), "PERMANENT");
    }
}
