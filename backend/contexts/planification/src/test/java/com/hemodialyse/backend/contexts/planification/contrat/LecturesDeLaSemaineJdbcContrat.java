package com.hemodialyse.backend.contexts.planification.contrat;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.planification.domain.CategorieDIsolement;
import com.hemodialyse.backend.contexts.planification.domain.JourDeLaSemaine;
import com.hemodialyse.backend.contexts.planification.domain.SemaineDuPlanning.PatientDeLaSemaine;
import com.hemodialyse.backend.contexts.planification.infrastructure.acl.legacyoptimisation.DeplacementsTemporairesJdbc;
import com.hemodialyse.backend.contexts.planification.infrastructure.acl.legacyorganisation.SeriesDuCentreJdbc;
import com.hemodialyse.backend.contexts.planification.infrastructure.acl.legacypatient.SejoursDesPatientsJdbc;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Date;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrat des lectures de la semaine (séjours des patients, déplacements temporaires, séries du centre ; les séances réalisées sont lues
 * par la Séance depuis SE7d) contre les
 * tables de la plateforme : H2 (schéma réduit créé par le test) et PostgreSQL 16 (schéma Flyway). Les règles de présence ne sont pas
 * ici (tests de domaine) : on vérifie la traduction des colonnes, les bornes de période et de centre.
 */
abstract class LecturesDeLaSemaineJdbcContrat {

    protected static final CentreId CENTRE = new CentreId(UUID.fromString("99997806-0000-0000-0000-00000000000a"));
    protected static final UUID AUTRE_CENTRE = UUID.fromString("99997806-0000-0000-0000-00000000000b");
    private static final UUID SALLE = UUID.fromString("99997806-0000-0000-0000-000000000011");
    private static final UUID CRENEAU = UUID.fromString("99997806-0000-0000-0000-000000000021");
    private static final UUID GENERATEUR = UUID.fromString("99997806-0000-0000-0000-000000000031");
    private static final UUID ALICE = UUID.fromString("99997806-0000-0000-0000-000000000041");
    private static final UUID BORIS = UUID.fromString("99997806-0000-0000-0000-000000000042");
    private static final UUID AUTRE_PATIENT = UUID.fromString("99997806-0000-0000-0000-000000000043");
    private static final LocalDate LUNDI = LocalDate.of(2026, 3, 2);

    protected abstract JdbcTemplate jdbc();

    private void patient(UUID id, UUID centre, String nom, String prenom, boolean sommeil, String etat, LocalDate evenement,
                         boolean placee) {
        jdbc().update("INSERT INTO patients (id, center_id, nom, prenom, sexe, numero_assurance, date_admission, type_patient, "
                        + "etat_patient, date_evenement_etat, en_sommeil, salle_id, position_id, generateur_id, jour_lundi, jour_mardi) "
                        + "VALUES (?, ?, ?, ?, 'M', ?, ?, 'NON_VACANCIER', ?, ?, ?, ?, ?, ?, TRUE, FALSE)", id, centre, nom, prenom,
                "ASS-" + id, Date.valueOf(LUNDI.minusYears(1)), etat, evenement == null ? null : Date.valueOf(evenement), sommeil,
                placee ? SALLE : null, placee ? CRENEAU : null, placee ? GENERATEUR : null);
    }

    @Test
    void should_read_the_stay_the_place_and_the_name_of_every_patient_of_the_centre_only() {
        patient(ALICE, CENTRE.valeur(), "Durand", "Alice", false, null, null, true);
        patient(BORIS, CENTRE.valeur(), "Martin", "Boris", true, "TRANSFERE", LUNDI, false);
        patient(AUTRE_PATIENT, AUTRE_CENTRE, "Autre", "Centre", false, null, null, true);

        var patients = new SejoursDesPatientsJdbc(jdbc()).duCentre(CENTRE);

        assertThat(patients).extracting(p -> p.nom()).containsExactly("Alice Durand", "Boris Martin");
        PatientDeLaSemaine alice = patients.get(0);
        assertThat(alice.place().salle().valeur()).isEqualTo(SALLE);
        assertThat(alice.place().creneau().valeur()).isEqualTo(CRENEAU);
        assertThat(alice.place().generateur().valeur()).isEqualTo(GENERATEUR);
        assertThat(alice.place().jours()).containsExactly(JourDeLaSemaine.LUNDI);
        assertThat(alice.place().categorie()).isEqualTo(CategorieDIsolement.SANS_RISQUE);
        assertThat(alice.sejour().enSommeil()).isFalse();
        assertThat(alice.sejour().dernierJourDePlace()).isNull();
        PatientDeLaSemaine boris = patients.get(1);
        assertThat(boris.place().salle()).isNull();
        assertThat(boris.sejour().enSommeil()).isTrue();
        assertThat(boris.sejour().partLeDernierJour()).as("transféré : part le dernier jour").isTrue();
        assertThat(boris.sejour().dernierJourDePlace()).isEqualTo(LUNDI);
    }

    @Test
    void should_not_read_the_serologies_when_only_the_stay_is_needed() {
        patient(ALICE, CENTRE.valeur(), "Durand", "Alice", false, null, null, true);
        jdbc().update("INSERT INTO serologies_patient (id, center_id, patient_id, marqueur, resultat, date_prelevement, created_at, "
                + "updated_at) VALUES (?, ?, ?, 'AG_HBS', 'POSITIF', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", UUID.randomUUID(),
                CENTRE.valeur(), ALICE, Date.valueOf(LUNDI));

        var complet = new SejoursDesPatientsJdbc(jdbc());

        assertThat(complet.duCentre(CENTRE).get(0).place().categorie()).isEqualTo(CategorieDIsolement.A_RISQUE);
        var sansRisque = complet.duCentreSansRisque(CENTRE);
        assertThat(sansRisque.get(0).place().categorie()).as("aucune sérologie lue").isEqualTo(CategorieDIsolement.SANS_RISQUE);
        assertThat(sansRisque.get(0).sejour()).isEqualTo(complet.duCentre(CENTRE).get(0).sejour());
    }

    // SE7d (ADR 0098) : « should_read_the_realised_sessions_of_the_period_with_their_frozen_place_and_ignore_the_others » est joué, sous le
    // même nom, par seance/.../contrat/SeancesRealiseesEtConstantesContrat (mémoire, H2, PG16) ; la traduction (nom, place, risque) par
    // planification/.../acl/seance/SeancesRealiseesPublieesTest. La Planification n'a plus de SQL sur seances.

    @Test
    void should_read_the_temporary_moves_of_the_period_of_the_centre_sorted_by_date() {
        patient(ALICE, CENTRE.valeur(), "Durand", "Alice", false, null, null, true);
        deplacement(CENTRE.valeur(), ALICE, LUNDI.plusDays(2));
        deplacement(CENTRE.valeur(), ALICE, LUNDI);
        deplacement(CENTRE.valeur(), ALICE, LUNDI.plusDays(20));
        deplacement(AUTRE_CENTRE, ALICE, LUNDI);

        var deplacements = new DeplacementsTemporairesJdbc(jdbc()).duCentre(CENTRE, LUNDI, LUNDI.plusDays(6));

        assertThat(deplacements).extracting(d -> d.date()).containsExactly(LUNDI, LUNDI.plusDays(2));
        assertThat(deplacements.get(0).generateur().valeur()).isEqualTo(GENERATEUR);
    }

    private void deplacement(UUID centre, UUID patient, LocalDate date) {
        jdbc().update("INSERT INTO deplacement_temporaire (id, center_id, patient_id, date_seance, salle_id, creneau_id, generateur_id, "
                + "cree_le) VALUES (?, ?, ?, ?, ?, ?, ?, ?)", UUID.randomUUID(), centre, patient, Date.valueOf(date), SALLE, CRENEAU,
                GENERATEUR, java.sql.Timestamp.valueOf(LUNDI.atStartOfDay()));
    }

    @Test
    void should_count_the_series_of_the_centre() {
        creneau(CENTRE.valeur(), "M");
        creneau(CENTRE.valeur(), "S");
        creneau(AUTRE_CENTRE, "M");

        assertThat(new SeriesDuCentreJdbc(jdbc()).nombre(CENTRE)).isEqualTo(2);
        assertThat(new SeriesDuCentreJdbc(jdbc()).nombre(new CentreId(UUID.randomUUID()))).isZero();
    }

    private void creneau(UUID centre, String code) {
        jdbc().update("INSERT INTO position_creneau (id, center_id, code, libelle) VALUES (?, ?, ?, ?)", UUID.randomUUID(), centre, code,
                "Libellé " + code);
    }
}
