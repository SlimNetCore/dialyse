package com.hemodialyse.backend.contexts.seance.infrastructure.acl;

import com.hemodialyse.backend.contexts.seance.domain.MoisDuTableauDeBord;
import com.hemodialyse.backend.contexts.seance.domain.PatientId;
import com.hemodialyse.backend.contexts.seance.infrastructure.acl.legacypatient.PatientsDuTableauDeBordJdbc;
import com.hemodialyse.backend.contexts.seance.infrastructure.persistence.SeancesRealiseesJdbc;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.Date;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lectures SQL du tableau de bord sur H2 (mode PostgreSQL) : séances réalisées du centre (bornes du mois incluses), patients et jointure des
 * présents (sans filtre du centre du patient : KB-SEA-65), identités. Lecture seule. Sur PostgreSQL 16 réel, les mêmes requêtes sont
 * exécutées par {@code backend/scripts/seance-se7b-pg16-rejeu.sh} (SELECT seule).
 */
class LecturesDuTableauDeBordJdbcTest {

    private static final CentreId A = new CentreId(new UUID(0, 0xA));
    private static final CentreId B = new CentreId(new UUID(0, 0xB));
    private static final MoisDuTableauDeBord JANVIER = new MoisDuTableauDeBord(2026, 1);

    private JdbcTemplate jdbc;

    @BeforeEach
    void avant() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:seance-tableau-de-bord;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("CREATE TABLE IF NOT EXISTS patients (id UUID PRIMARY KEY, center_id UUID NOT NULL, nom VARCHAR(100), prenom VARCHAR(100), "
                + "sexe VARCHAR(10), date_naissance DATE, date_admission DATE, en_sommeil BOOLEAN, etat_patient VARCHAR(30), "
                + "date_evenement_etat DATE, jour_lundi BOOLEAN, jour_mardi BOOLEAN, jour_mercredi BOOLEAN, jour_jeudi BOOLEAN, "
                + "jour_vendredi BOOLEAN, jour_samedi BOOLEAN, jour_dimanche BOOLEAN)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS seances (id UUID PRIMARY KEY, center_id UUID NOT NULL, patient_id UUID NOT NULL, "
                + "date_seance DATE NOT NULL, statut VARCHAR(20) NOT NULL)");
        jdbc.update("DELETE FROM seances");
        jdbc.update("DELETE FROM patients");
    }

    private void patient(int n, CentreId centre, String nom, String sexe, boolean sommeil, boolean lundi, boolean dimanche) {
        jdbc.update("INSERT INTO patients (id, center_id, nom, prenom, sexe, date_naissance, date_admission, en_sommeil, etat_patient, "
                        + "date_evenement_etat, jour_lundi, jour_mardi, jour_mercredi, jour_jeudi, jour_vendredi, jour_samedi, jour_dimanche) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, FALSE, FALSE, FALSE, FALSE, FALSE, ?)",
                new UUID(1, n), centre.valeur(), nom, "Prenom" + n, sexe, Date.valueOf("1970-03-04"), Date.valueOf("2020-01-01"), sommeil,
                "TRANSFERE", Date.valueOf("2026-01-20"), lundi, dimanche);
    }

    private void seance(int n, CentreId centre, int patient, String jour, String statut) {
        jdbc.update("INSERT INTO seances (id, center_id, patient_id, date_seance, statut) VALUES (?, ?, ?, ?, ?)", new UUID(2, n),
                centre.valeur(), new UUID(1, patient), Date.valueOf(jour), statut);
    }

    @Test
    void should_read_every_patient_of_the_centre_with_their_dialysis_days_state_and_sleep() {
        patient(1, A, "Alpha", "F", false, true, false);
        patient(2, A, "Beta", "M", true, false, true);
        patient(3, B, "Gamma", "M", false, true, true);

        var patients = new PatientsDuTableauDeBordJdbc(jdbc).duCentre(A);

        assertThat(patients).hasSize(2);
        var alpha = patients.stream().filter(p -> p.id().equals(new PatientId(new UUID(1, 1)))).findFirst().orElseThrow();
        assertThat(alpha.joursDeDialyse()).isEqualTo(Set.of(DayOfWeek.MONDAY));
        assertThat(alpha.enSommeil()).isFalse();
        assertThat(alpha.etat()).isEqualTo("TRANSFERE");
        assertThat(alpha.dateDeLEtat()).isEqualTo(LocalDate.of(2026, 1, 20));
        assertThat(alpha.naissance()).isEqualTo(LocalDate.of(1970, 3, 4));
        assertThat(alpha.admission()).isEqualTo(LocalDate.of(2020, 1, 1));
        assertThat(patients.stream().filter(p -> p.enSommeil()).findFirst().orElseThrow().joursDeDialyse()).isEqualTo(Set.of(DayOfWeek.SUNDAY));
    }

    @Test
    void should_count_and_list_only_performed_sessions_of_the_month_of_the_centre_bounds_included() {
        patient(1, A, "Alpha", "F", false, true, false);
        seance(1, A, 1, "2026-01-01", "VALIDEE");
        seance(2, A, 1, "2026-01-31", "FACTUREE");
        seance(3, A, 1, "2026-01-15", "SIGNEE");
        seance(4, A, 1, "2026-01-16", "CREE");
        seance(5, A, 1, "2026-01-17", "BROUILLON");
        seance(6, A, 1, "2026-01-18", "validee");
        seance(7, A, 1, "2025-12-31", "SIGNEE");
        seance(8, A, 1, "2026-02-01", "SIGNEE");
        seance(9, B, 1, "2026-01-10", "SIGNEE");
        var seances = new SeancesRealiseesJdbc(jdbc);

        assertThat(seances.nombre(A, JANVIER)).isEqualTo(3);
        assertThat(seances.du(A, JANVIER)).extracting(s -> s.jour().toString()).containsExactlyInAnyOrder("2026-01-01", "2026-01-31", "2026-01-15");
        assertThat(seances.nombre(B, JANVIER)).isEqualTo(1);
        assertThat(seances.nombre(A, new MoisDuTableauDeBord(2024, 2))).isZero();
    }

    @Test
    void should_return_each_present_patient_once_even_when_he_belongs_to_another_centre_KB_SEA_65() {
        patient(1, A, "Alpha", "F", false, true, false);
        patient(2, B, "Etranger", "M", false, true, false);
        seance(1, A, 1, "2026-01-05", "SIGNEE");
        seance(2, A, 1, "2026-01-07", "SIGNEE");
        seance(3, A, 2, "2026-01-07", "SIGNEE");
        seance(4, A, 99, "2026-01-07", "SIGNEE");

        var presents = new PatientsDuTableauDeBordJdbc(jdbc).presentsDuMois(A, JANVIER);

        assertThat(presents).extracting(d -> d.patient().valeur()).containsExactlyInAnyOrder(new UUID(1, 1), new UUID(1, 2));
    }

    @Test
    void should_read_the_identities_of_the_patients_of_the_centre_only_and_never_write() {
        patient(1, A, "Alpha", "F", false, true, false);
        patient(2, B, "Etranger", "M", false, true, false);

        var identites = new PatientsDuTableauDeBordJdbc(jdbc).identites(A);

        assertThat(identites).containsOnlyKeys(new PatientId(new UUID(1, 1)));
        assertThat(identites.get(new PatientId(new UUID(1, 1))).nom()).isEqualTo("Alpha");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM patients", Integer.class)).isEqualTo(2);
    }
}
