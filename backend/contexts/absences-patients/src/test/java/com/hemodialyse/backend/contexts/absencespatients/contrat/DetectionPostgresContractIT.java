package com.hemodialyse.backend.contexts.absencespatients.contrat;

import com.hemodialyse.backend.contexts.absencespatients.domain.AbsencePatient;
import com.hemodialyse.backend.contexts.absencespatients.domain.DateOuverte;
import com.hemodialyse.backend.contexts.absencespatients.domain.ForfaitApplicable;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.AbsenceId;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.ForfaitId;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.PatientId;
import com.hemodialyse.backend.contexts.absencespatients.domain.Valorisation;
import com.hemodialyse.backend.contexts.absencespatients.infrastructure.acl.legacyorganisation.CentresActifsJdbc;
import com.hemodialyse.backend.contexts.absencespatients.infrastructure.acl.legacyplanning.PatientsAttendusJdbc;
import com.hemodialyse.backend.contexts.absencespatients.infrastructure.persistence.AbsencesJdbc;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Détection de nuit (slice A3) sur un PostgreSQL 16 RÉEL (schéma Flyway V1..V43) : le patient attendu (règle de sortie
 * uniformisée, séjour limité, fériés, séances réalisées, tri par identifiant) sur les vraies tables de la plateforme, les centres
 * actifs, et le curseur de la réconciliation avec des identifiants aléatoires (l'ordre des UUID de PostgreSQL est celui de la base
 * même). Base JETABLE uniquement : {@code -Dabsences.pg16.url=jdbc:postgresql://…} (script {@code absences-a3-pg16-rejeu.sh}).
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=${absences.pg16.url}",
        "spring.datasource.username=${absences.pg16.user:absences_test}",
        "spring.datasource.password=${absences.pg16.password:}",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.sql.init.mode=never"
})
@EnabledIfSystemProperty(named = "absences.pg16.url", matches = ".+")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DetectionPostgresContractIT {

    private static final UUID SOCIETE = new UUID(0xB000, 1);
    private static final CentreId CENTRE = new CentreId(new UUID(0xB000, 0xa));
    private static final CentreId INACTIF = new CentreId(new UUID(0xB000, 0xb));
    /** Un lundi. */
    private static final LocalDate JOUR = LocalDate.of(2026, 10, 19);

    @Autowired
    private JdbcTemplate jdbc;

    private int rang;

    @BeforeEach
    void preparer() {
        nettoyer();
        jdbc.update("INSERT INTO societes (id, code, raison_sociale, actif, created_at) VALUES (?, 'ZT-A3', 'Société A3', TRUE, now())",
                SOCIETE);
        jdbc.update("INSERT INTO centers (id, code, name, societe_id, actif) VALUES (?, 'ZT-A3-A', 'Centre A3', ?, TRUE)",
                CENTRE.valeur(), SOCIETE);
        jdbc.update("INSERT INTO centers (id, code, name, societe_id, actif) VALUES (?, 'ZT-A3-B', 'Centre inactif', ?, FALSE)",
                INACTIF.valeur(), SOCIETE);
    }

    @AfterEach
    void nettoyer() {
        for (String table : List.of("absence_patient", "seances", "center_holiday", "patients")) {
            jdbc.update("DELETE FROM " + table + " WHERE center_id IN (?, ?)", CENTRE.valeur(), INACTIF.valeur());
        }
        jdbc.update("DELETE FROM centers WHERE code LIKE 'ZT-A3-%'");
        jdbc.update("DELETE FROM societes WHERE code = 'ZT-A3'");
    }

    private PatientId patient(String etat, LocalDate evenement) {
        UUID id = new UUID(0xB001, ++rang);
        jdbc.update("INSERT INTO patients (id, center_id, nom, prenom, sexe, numero_assurance, date_admission, type_patient, "
                        + "etat_patient, date_evenement_etat, jour_lundi, en_sommeil) VALUES (?, ?, 'NOM', 'Prenom', 'M', ?, ?, "
                        + "'NON_VACANCIER', ?, ?, TRUE, FALSE)", id, CENTRE.valeur(), "ASS-" + rang, Date.valueOf(JOUR.minusYears(1)),
                etat, evenement == null ? null : Date.valueOf(evenement));
        return new PatientId(id);
    }

    private List<PatientId> attendus(LocalDate jour) {
        return new PatientsAttendusJdbc(jdbc).sansSeanceRealisee(CENTRE, jour);
    }

    @Test
    void la_base_est_bien_postgresql_16() {
        assertThat(jdbc.queryForObject("SELECT version()", String.class)).startsWith("PostgreSQL 16");
    }

    @Test
    void la_regle_de_sortie_est_uniforme_et_le_sejour_limite_va_jusqu_a_sa_fin_incluse() {
        for (String etat : List.of("DECEDE", "GREFFE", "TRANSFERE", "GUERRI")) {
            PatientId veille = patient(etat, JOUR.plusDays(1));
            PatientId lejour = patient(etat, JOUR);
            PatientId sansDate = patient(etat, null);

            assertThat(attendus(JOUR)).as(etat).contains(veille).doesNotContain(lejour, sansDate);
        }
        PatientId jusquAuJour = patient("OCCASIONNEL", JOUR);
        PatientId termine = patient("VACANCIER_LOCAL", JOUR.minusDays(1));
        PatientId permanent = patient("PERMANENT", null);

        assertThat(attendus(JOUR)).contains(jusquAuJour, permanent).doesNotContain(termine);
    }

    @Test
    void ferie_et_seance_realisee_retirent_le_patient_et_le_resultat_est_trie_par_identifiant() {
        PatientId premier = patient(null, null);
        PatientId realise = patient(null, null);
        PatientId cree = patient(null, null);
        jdbc.update("INSERT INTO seances (id, center_id, patient_id, date_seance, statut) VALUES (?, ?, ?, ?, 'VALIDEE')",
                UUID.randomUUID(), CENTRE.valeur(), realise.valeur(), Date.valueOf(JOUR));
        jdbc.update("INSERT INTO seances (id, center_id, patient_id, date_seance, statut) VALUES (?, ?, ?, ?, 'CREE')",
                UUID.randomUUID(), CENTRE.valeur(), cree.valeur(), Date.valueOf(JOUR));

        assertThat(attendus(JOUR)).containsExactly(premier, cree);

        jdbc.update("INSERT INTO center_holiday (id, center_id, day_date, label) VALUES (?, ?, ?, 'Férié')", UUID.randomUUID(),
                CENTRE.valeur(), Date.valueOf(JOUR));
        assertThat(attendus(JOUR)).isEmpty();
    }

    @Test
    void un_centre_inactif_n_est_pas_parcouru() {
        assertThat(new CentresActifsJdbc(jdbc).liste()).contains(CENTRE).doesNotContain(INACTIF);
    }

    @Test
    void le_curseur_de_reconciliation_visite_chaque_absence_une_fois_avec_des_identifiants_aleatoires() {
        AbsencesJdbc absences = new AbsencesJdbc(jdbc);
        Valorisation valeur = Valorisation.depuis(new ForfaitApplicable(new ForfaitId(UUID.randomUUID()), "Forfait HD",
                new BigDecimal("10000.00")), new BigDecimal("19"));
        Set<AbsenceId> creees = new HashSet<>();
        for (int i = 0; i < 25; i++) {
            AbsenceId id = new AbsenceId(UUID.randomUUID());
            absences.creer(AbsencePatient.detecter(id, new DateOuverte(CENTRE, new PatientId(UUID.randomUUID()), JOUR), valeur,
                    Instant.parse("2026-10-20T01:30:00Z")));
            creees.add(id);
        }

        List<AbsenceId> visitees = new ArrayList<>();
        AbsenceId curseur = null;
        List<AbsencePatient> page;
        do {
            page = absences.detecteesAQualifierEntre(CENTRE, JOUR, JOUR, curseur, 7);
            for (AbsencePatient lue : page) {
                visitees.add(lue.id());
                curseur = lue.id();
            }
        } while (page.size() == 7);

        assertThat(visitees).hasSize(25).doesNotHaveDuplicates();
        assertThat(new HashSet<>(visitees)).isEqualTo(creees);
    }
}
