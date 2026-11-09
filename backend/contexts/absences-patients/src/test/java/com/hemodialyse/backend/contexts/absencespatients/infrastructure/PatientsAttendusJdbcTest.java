package com.hemodialyse.backend.contexts.absencespatients.infrastructure;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.PatientId;
import com.hemodialyse.backend.contexts.absencespatients.infrastructure.acl.legacyorganisation.CentresActifsJdbc;
import com.hemodialyse.backend.contexts.absencespatients.infrastructure.acl.legacyplanning.PatientsAttendusJdbc;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le patient attendu (RG-ABS-041) et les centres actifs, sur les tables de la plateforme (SQL direct, ADR 0024) : règle de sortie
 * uniformisée (KB-ABS-24 : décès, greffe, transfert, guérison excluent le jour de l'événement ; sans date d'événement ils ne sont jamais
 * attendus, comme avant A3), séjour limité, programme, sommeil, admission, fériés, fermetures, séance réalisée, centre.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:absences-acl-attendus;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PatientsAttendusJdbcTest {

    private static final CentreId CENTRE = new CentreId(new UUID(0xA000, 1));
    private static final CentreId AUTRE = new CentreId(new UUID(0xA000, 2));
    private static final CentreId INACTIF = new CentreId(new UUID(0xA000, 3));
    private static final CentreId SANS_INDICATION = new CentreId(new UUID(0xA000, 4));
    /** Un lundi. */
    private static final LocalDate JOUR = LocalDate.of(2026, 10, 19);
    private static final LocalDate EVENEMENT = JOUR;

    @Autowired
    private JdbcTemplate jdbc;

    private int rang;

    private PatientsAttendusJdbc attendus() {
        // la présence est celle de la planification (langage publié) sur les tables de la plateforme : l'oracle de la règle reste ici
        // depuis SE7d (ADR 0098), les séances réalisées arrivent par le langage fourni : double qui lit la table semée ici
        return new PatientsAttendusJdbc(jdbc, new com.hemodialyse.backend.contexts.planification.application.ConsulterLaPresenceDesPatients(
                new com.hemodialyse.backend.contexts.planification.infrastructure.acl.legacypatient.SejoursDesPatientsJdbc(jdbc,
                        centre -> java.util.Set.of())),
                new com.hemodialyse.backend.contexts.absencespatients.contrat.SeancesRealiseesSurLaTable(jdbc));
    }

    @BeforeEach
    void preparer() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS patients (id UUID PRIMARY KEY, center_id UUID NOT NULL, jour_lundi BOOLEAN, "
                + "jour_mardi BOOLEAN, jour_mercredi BOOLEAN, jour_jeudi BOOLEAN, jour_vendredi BOOLEAN, jour_samedi BOOLEAN, "
                + "jour_dimanche BOOLEAN, en_sommeil BOOLEAN, date_admission DATE, etat_patient VARCHAR(40), "
                + "date_evenement_etat DATE, nom VARCHAR(100), prenom VARCHAR(100), salle_id UUID, position_id UUID, generateur_id UUID)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS serologies_patient (id UUID PRIMARY KEY, center_id UUID NOT NULL, patient_id UUID NOT NULL, "
                + "marqueur VARCHAR(20), resultat VARCHAR(20), date_prelevement DATE, created_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS center_holiday (id UUID PRIMARY KEY, center_id UUID NOT NULL, day_date DATE)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS center_closure_day (id UUID PRIMARY KEY, center_id UUID NOT NULL, day_date DATE)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS seances (id UUID PRIMARY KEY, center_id UUID NOT NULL, patient_id UUID NOT NULL, "
                + "date_seance DATE NOT NULL, statut VARCHAR(20))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS centers (id UUID PRIMARY KEY, actif BOOLEAN)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS absence_patient (id UUID PRIMARY KEY, center_id UUID NOT NULL, patient_id UUID NOT NULL, "
                + "date_seance DATE NOT NULL)");
        for (String table : new String[]{"absence_patient", "seances", "center_closure_day", "center_holiday", "patients", "centers"}) {
            jdbc.update("DELETE FROM " + table);
        }
        jdbc.update("INSERT INTO centers (id, actif) VALUES (?, TRUE)", CENTRE.valeur());
        jdbc.update("INSERT INTO centers (id, actif) VALUES (?, TRUE)", AUTRE.valeur());
        jdbc.update("INSERT INTO centers (id, actif) VALUES (?, FALSE)", INACTIF.valeur());
        jdbc.update("INSERT INTO centers (id, actif) VALUES (?, NULL)", SANS_INDICATION.valeur());
    }

    private PatientId patient(CentreId centre, boolean lundi, Boolean sommeil, LocalDate admission, String etat, LocalDate evenement) {
        UUID id = new UUID(0xA001, ++rang);
        jdbc.update("INSERT INTO patients (id, center_id, jour_lundi, jour_mardi, jour_mercredi, jour_jeudi, jour_vendredi, "
                        + "jour_samedi, jour_dimanche, en_sommeil, date_admission, etat_patient, date_evenement_etat) "
                        + "VALUES (?, ?, ?, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, ?, ?, ?, ?)", id, centre.valeur(), lundi,
                sommeil, Date.valueOf(admission), etat, evenement == null ? null : Date.valueOf(evenement));
        return new PatientId(id);
    }

    private PatientId attenduParDefaut() {
        return patient(CENTRE, true, false, JOUR.minusYears(1), null, null);
    }

    @ParameterizedTest(name = "{0} le {1} jour(s) apres l''evenement : attendu = {2}")
    @CsvSource({
            "DECEDE, -1, true", "DECEDE, 0, false", "DECEDE, 1, false",
            "GREFFE, -1, true", "GREFFE, 0, false", "GREFFE, 1, false",
            "TRANSFERE, -1, true", "TRANSFERE, 0, false", "TRANSFERE, 1, false",
            "GUERRI, -1, true", "GUERRI, 0, false", "GUERRI, 1, false",
            "OCCASIONNEL, -1, true", "OCCASIONNEL, 0, true", "OCCASIONNEL, 1, false",
            "VACANCIER_LOCAL, 0, true", "VACANCIER_LOCAL, 1, false",
            "VACANCIER_ETRANGER, 0, true", "VACANCIER_ETRANGER, 1, false",
            "PERMANENT, 1, true", "AUTRE_ETAT, 1, true"})
    void un_patient_sorti_n_est_plus_attendu_des_le_jour_de_l_evenement_un_sejour_limite_jusqu_a_sa_fin_incluse(
            String etat, int joursApresLEvenement, boolean attendu) {
        PatientId patient = patient(CENTRE, true, false, JOUR.minusYears(1), etat, JOUR.minusDays(joursApresLEvenement));

        assertThat(attendus().sansSeanceRealisee(CENTRE, JOUR).contains(patient)).isEqualTo(attendu);
    }

    @ParameterizedTest(name = "{0} sans date d''evenement : attendu = {1}")
    @CsvSource({"DECEDE, false", "GREFFE, false", "TRANSFERE, false", "GUERRI, false", "OCCASIONNEL, true",
            "VACANCIER_LOCAL, true", "VACANCIER_ETRANGER, true", "PERMANENT, true"})
    void sans_date_d_evenement_un_patient_sorti_n_est_jamais_attendu_mais_un_sejour_limite_l_est(String etat, boolean attendu) {
        PatientId patient = patient(CENTRE, true, false, JOUR.minusYears(1), etat, null);

        assertThat(attendus().sansSeanceRealisee(CENTRE, JOUR).contains(patient)).isEqualTo(attendu);
    }

    @Test
    void le_patient_sans_etat_est_attendu() {
        PatientId patient = attenduParDefaut();

        assertThat(attendus().sansSeanceRealisee(CENTRE, JOUR)).containsExactly(patient);
    }

    @Test
    void il_faut_le_jour_au_programme_ni_sommeil_ni_admission_future() {
        PatientId attendu = attenduParDefaut();
        patient(CENTRE, false, false, JOUR.minusYears(1), null, null);
        patient(CENTRE, true, true, JOUR.minusYears(1), null, null);
        patient(CENTRE, true, false, JOUR.plusDays(1), null, null);
        PatientId admisCeJour = patient(CENTRE, true, false, JOUR, null, null);
        PatientId sommeilNul = patient(CENTRE, true, null, JOUR.minusYears(1), null, null);

        assertThat(attendus().sansSeanceRealisee(CENTRE, JOUR)).containsExactlyInAnyOrder(attendu, admisCeJour, sommeilNul);
    }

    @Test
    void le_programme_suit_le_jour_de_la_semaine() {
        PatientId patient = attenduParDefaut();

        assertThat(attendus().sansSeanceRealisee(CENTRE, JOUR.plusDays(1))).isEmpty();
        assertThat(attendus().sansSeanceRealisee(CENTRE, JOUR.plusDays(7))).containsExactly(patient);
    }

    @Test
    void un_jour_ferie_ou_de_fermeture_du_centre_n_attend_personne_mais_pas_ceux_d_un_autre_centre() {
        attenduParDefaut();
        PatientId autreCentre = patient(AUTRE, true, false, JOUR.minusYears(1), null, null);
        jdbc.update("INSERT INTO center_holiday (id, center_id, day_date) VALUES (?, ?, ?)", UUID.randomUUID(), CENTRE.valeur(),
                Date.valueOf(JOUR));
        jdbc.update("INSERT INTO center_closure_day (id, center_id, day_date) VALUES (?, ?, ?)", UUID.randomUUID(), CENTRE.valeur(),
                Date.valueOf(JOUR.plusDays(7)));

        assertThat(attendus().sansSeanceRealisee(CENTRE, JOUR)).isEmpty();
        assertThat(attendus().sansSeanceRealisee(CENTRE, JOUR.plusDays(7))).isEmpty();
        assertThat(attendus().sansSeanceRealisee(CENTRE, JOUR.plusDays(14))).hasSize(1);
        assertThat(attendus().sansSeanceRealisee(AUTRE, JOUR)).containsExactly(autreCentre);
    }

    @ParameterizedTest
    @CsvSource({"VALIDEE, false", "SIGNEE, false", "FACTUREE, false", "CREE, true", "ABSENT, true", "BROUILLON, true"})
    void seule_une_seance_realisee_retire_le_patient_des_attendus(String statut, boolean toujoursAttendu) {
        PatientId patient = attenduParDefaut();
        jdbc.update("INSERT INTO seances (id, center_id, patient_id, date_seance, statut) VALUES (?, ?, ?, ?, ?)", UUID.randomUUID(),
                CENTRE.valeur(), patient.valeur(), Date.valueOf(JOUR), statut);

        assertThat(attendus().sansSeanceRealisee(CENTRE, JOUR).contains(patient)).isEqualTo(toujoursAttendu);
    }

    @Test
    void une_seance_d_un_autre_jour_ou_d_un_autre_centre_ne_retire_pas_le_patient() {
        PatientId patient = attenduParDefaut();
        jdbc.update("INSERT INTO seances (id, center_id, patient_id, date_seance, statut) VALUES (?, ?, ?, ?, 'VALIDEE')",
                UUID.randomUUID(), CENTRE.valeur(), patient.valeur(), Date.valueOf(JOUR.plusDays(1)));
        jdbc.update("INSERT INTO seances (id, center_id, patient_id, date_seance, statut) VALUES (?, ?, ?, ?, 'VALIDEE')",
                UUID.randomUUID(), AUTRE.valeur(), patient.valeur(), Date.valueOf(JOUR));

        assertThat(attendus().sansSeanceRealisee(CENTRE, JOUR)).containsExactly(patient);
    }

    @Test
    void un_patient_dont_l_absence_existe_deja_ce_jour_la_n_est_plus_rendu_mais_reste_attendu_les_autres_jours() {
        PatientId detecte = attenduParDefaut();
        PatientId autre = attenduParDefaut();
        jdbc.update("INSERT INTO absence_patient (id, center_id, patient_id, date_seance) VALUES (?, ?, ?, ?)", UUID.randomUUID(),
                CENTRE.valeur(), detecte.valeur(), Date.valueOf(JOUR));
        jdbc.update("INSERT INTO absence_patient (id, center_id, patient_id, date_seance) VALUES (?, ?, ?, ?)", UUID.randomUUID(),
                AUTRE.valeur(), autre.valeur(), Date.valueOf(JOUR));

        assertThat(attendus().sansSeanceRealisee(CENTRE, JOUR)).containsExactly(autre);
        assertThat(attendus().sansSeanceRealisee(CENTRE, JOUR.plusDays(7))).containsExactly(detecte, autre);
    }

    @Test
    void les_patients_attendus_sortent_par_identifiant_croissant() {
        PatientId premier = attenduParDefaut();
        PatientId second = attenduParDefaut();
        PatientId troisieme = attenduParDefaut();

        assertThat(attendus().sansSeanceRealisee(CENTRE, JOUR)).containsExactly(premier, second, troisieme);
    }

    @Test
    void un_centre_inactif_n_est_pas_parcouru_mais_un_centre_sans_indication_l_est() {
        var centres = new CentresActifsJdbc(jdbc).liste();

        assertThat(centres).containsExactlyInAnyOrder(CENTRE, AUTRE, SANS_INDICATION).doesNotContain(INACTIF);
    }
}
