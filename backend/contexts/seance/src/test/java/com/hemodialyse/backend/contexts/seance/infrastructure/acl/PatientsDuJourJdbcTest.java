package com.hemodialyse.backend.contexts.seance.infrastructure.acl;

import com.hemodialyse.backend.contexts.seance.application.PatientsDuJour;
import com.hemodialyse.backend.contexts.seance.infrastructure.acl.legacypatient.PatientsDuJourJdbc;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.Date;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** ACL des patients du jour sur H2 (mode PostgreSQL) : jointure interne, tri par nom puis prénom, tous statuts, lecture seule. */
class PatientsDuJourJdbcTest {

    private static final CentreId A = new CentreId(new UUID(0, 0xA));
    private static final CentreId B = new CentreId(new UUID(0, 0xB));
    private static final LocalDate J = LocalDate.of(2026, 6, 15);

    private JdbcTemplate jdbc;

    @BeforeEach
    void avant() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:seance-patients-du-jour;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("CREATE TABLE IF NOT EXISTS patients (id UUID PRIMARY KEY, code_patient VARCHAR(50), nom VARCHAR(100), prenom VARCHAR(100))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS seances (id UUID PRIMARY KEY, center_id UUID NOT NULL, patient_id UUID NOT NULL, "
                + "date_seance DATE NOT NULL, statut VARCHAR(20) NOT NULL)");
        jdbc.update("DELETE FROM seances");
        jdbc.update("DELETE FROM patients");
    }

    private void patient(int n, String code, String nom, String prenom) {
        jdbc.update("INSERT INTO patients (id, code_patient, nom, prenom) VALUES (?, ?, ?, ?)", new UUID(1, n), code, nom, prenom);
    }

    private void seance(int n, CentreId centre, int patient, LocalDate jour, String statut) {
        jdbc.update("INSERT INTO seances (id, center_id, patient_id, date_seance, statut) VALUES (?, ?, ?, ?, ?)", new UUID(2, n),
                centre.valeur(), new UUID(1, patient), Date.valueOf(jour), statut);
    }

    @Test
    void should_list_every_session_of_the_day_whatever_its_status_sorted_by_last_name_then_first_name() {
        patient(1, "P1", "Zed", "Anne");
        patient(2, "P2", "Alpha", "Zoe");
        patient(3, "P3", "Alpha", "Ada");
        seance(1, A, 1, J, "SIGNEE");
        seance(2, A, 2, J, "CREE");
        seance(3, A, 3, J, "ABSENT");

        var lignes = new PatientsDuJourJdbc(jdbc).deLaJournee(A, J);

        assertThat(lignes).extracting(PatientsDuJour.PatientDuJour::code).containsExactly("P3", "P2", "P1");
        assertThat(lignes).extracting(PatientsDuJour.PatientDuJour::statut).containsExactly("ABSENT", "CREE", "SIGNEE");
        assertThat(lignes.get(0).seance().valeur()).isEqualTo(new UUID(2, 3));
        assertThat(lignes.get(0).patient().valeur()).isEqualTo(new UUID(1, 3));
        assertThat(lignes.get(0).jour()).isEqualTo(J);
        assertThat(lignes.get(0).nom()).isEqualTo("Alpha");
        assertThat(lignes.get(0).prenom()).isEqualTo("Ada");
    }

    @Test
    void should_exclude_other_days_other_centres_and_sessions_whose_patient_no_longer_exists() {
        patient(1, "P1", "Alpha", "Ada");
        seance(1, A, 1, J, "SIGNEE");
        seance(2, A, 1, J.minusDays(1), "SIGNEE");
        seance(3, B, 1, J, "SIGNEE");
        seance(4, A, 99, J, "SIGNEE");

        assertThat(new PatientsDuJourJdbc(jdbc).deLaJournee(A, J)).extracting(l -> l.seance().valeur()).containsExactly(new UUID(2, 1));
        assertThat(new PatientsDuJourJdbc(jdbc).deLaJournee(B, J.plusDays(1))).isEmpty();
    }

    @Test
    void should_never_write() {
        patient(1, "P1", "Alpha", "Ada");
        seance(1, A, 1, J, "SIGNEE");

        new PatientsDuJourJdbc(jdbc).deLaJournee(A, J);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM seances", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM patients", Integer.class)).isEqualTo(1);
    }
}
