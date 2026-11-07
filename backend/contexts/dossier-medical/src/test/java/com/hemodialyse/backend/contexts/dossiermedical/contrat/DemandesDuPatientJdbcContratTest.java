package com.hemodialyse.backend.contexts.dossiermedical.contrat;

import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence.DemandesDuPatientJdbc;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrat de {@code DemandesDuPatient} sur la table réelle {@code demandes_examen} (lecture seule) : une demande n'existe que pour SON
 * patient dans SON centre (écart décidé KB-MED-77, ferme KB-MED-44).
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:dossier-medical-demandes-du-patient;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DemandesDuPatientJdbcContratTest {
    private static final CentreId CENTRE_A = new CentreId(UUID.fromString("00000000-0000-0000-0000-00000000d6a1"));
    private static final CentreId CENTRE_B = new CentreId(UUID.fromString("00000000-0000-0000-0000-00000000d6b2"));
    private static final PatientId PATIENT = new PatientId(UUID.fromString("00000000-0000-0000-0000-0000000d6001"));
    private static final PatientId AUTRE_PATIENT = new PatientId(UUID.fromString("00000000-0000-0000-0000-0000000d6002"));

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void avant() {
        DemandesDExamenJdbcContrat.creerLesTables(jdbc);
        vider();
    }

    @AfterEach
    void apres() {
        vider();
    }

    private void vider() {
        jdbc.update("DELETE FROM demandes_examen WHERE center_id IN (?, ?)", CENTRE_A.valeur(), CENTRE_B.valeur());
    }

    @Test
    void une_demande_existe_pour_son_patient_et_son_centre_seulement() {
        UUID demande = UUID.randomUUID();
        jdbc.update("INSERT INTO demandes_examen (id, patient_id, center_id, prescripteur_id, date_demande, categorie, urgent, motif, statut, "
                        + "conclusion, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)", demande, PATIENT.valeur(), CENTRE_A.valeur(), "dr",
                java.sql.Date.valueOf("2026-01-05"), "BIOLOGIE", false, null, "DEMANDE", null, Timestamp.from(Instant.parse("2026-01-01T10:00:00Z")),
                Timestamp.from(Instant.parse("2026-01-01T10:00:00Z")));
        var demandes = new DemandesDuPatientJdbc(jdbc);
        assertThat(demandes.existe(CENTRE_A, PATIENT, demande)).isTrue();
        assertThat(demandes.existe(CENTRE_B, PATIENT, demande)).isFalse();
        assertThat(demandes.existe(CENTRE_A, AUTRE_PATIENT, demande)).isFalse();
        assertThat(demandes.existe(CENTRE_A, PATIENT, UUID.randomUUID())).isFalse();
    }
}
