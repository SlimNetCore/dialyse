package com.hemodialyse.backend.contexts.dossiermedical.contrat;

import com.hemodialyse.backend.contexts.dossiermedical.domain.MarqueurSerologique;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.ResultatSerologique;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Serologie;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Serologies;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Contrat du port {@link Serologies} : le double en mémoire, H2 et PostgreSQL 16 passent le MÊME jeu de tests. */
abstract class SerologiesContrat {

    static final CentreId CENTRE_A = new CentreId(UUID.fromString("00000000-0000-0000-0000-00000000d4a1"));
    static final CentreId CENTRE_B = new CentreId(UUID.fromString("00000000-0000-0000-0000-00000000d4b2"));
    static final PatientId PATIENT = new PatientId(UUID.fromString("00000000-0000-0000-0000-0000000d4001"));
    static final PatientId AUTRE_PATIENT = new PatientId(UUID.fromString("00000000-0000-0000-0000-0000000d4002"));
    static final Instant T0 = Instant.parse("2026-01-01T10:00:00Z");
    static final LocalDate AUJOURDHUI = LocalDate.of(2026, 6, 15);

    protected abstract Serologies serologies();

    static Serologie serologie(CentreId centre, PatientId patient, MarqueurSerologique marqueur, LocalDate date, Instant creee) {
        return Serologie.enregistrer(UUID.randomUUID(), patient, centre, marqueur, ResultatSerologique.NEGATIF, null, null, date, AUJOURDHUI,
                null, null, null, creee);
    }

    @Test
    void une_serologie_ajoutee_est_relue_avec_tous_ses_champs() {
        var s = Serologie.enregistrer(UUID.randomUUID(), PATIENT, CENTRE_A, MarqueurSerologique.AC_VHC, ResultatSerologique.POSITIF,
                new BigDecimal("4.5"), "S/CO", LocalDate.of(2026, 1, 5), AUJOURDHUI, "Laboratoire fictif", LocalDate.of(2026, 4, 5),
                "Isoler (fictif)", T0);
        serologies().ajouter(s);
        var relue = serologies().trouverEnVerrouillant(CENTRE_A, PATIENT, s.id()).orElseThrow();
        assertThat(relue.marqueur()).isEqualTo(MarqueurSerologique.AC_VHC);
        assertThat(relue.resultat()).isEqualTo(ResultatSerologique.POSITIF);
        assertThat(relue.titre()).isEqualByComparingTo("4.5");
        assertThat(relue.unite()).isEqualTo("S/CO");
        assertThat(relue.datePrelevement()).isEqualTo(LocalDate.of(2026, 1, 5));
        assertThat(relue.laboratoire()).isEqualTo("Laboratoire fictif");
        assertThat(relue.dateProchainControle()).isEqualTo(LocalDate.of(2026, 4, 5));
        assertThat(relue.conduiteATenir()).isEqualTo("Isoler (fictif)");
        assertThat(relue.creeeLe()).isEqualTo(T0);
        assertThat(relue.modifieeLe()).isEqualTo(T0);
        assertThat(relue.centre()).isEqualTo(CENTRE_A);
        assertThat(relue.patient()).isEqualTo(PATIENT);
    }

    @Test
    void les_champs_facultatifs_absents_restent_absents() {
        var s = serologie(CENTRE_A, PATIENT, MarqueurSerologique.TPHA, LocalDate.of(2026, 1, 5), T0);
        serologies().ajouter(s);
        var relue = serologies().trouverEnVerrouillant(CENTRE_A, PATIENT, s.id()).orElseThrow();
        assertThat(relue.titre()).isNull();
        assertThat(relue.unite()).isNull();
        assertThat(relue.laboratoire()).isNull();
        assertThat(relue.dateProchainControle()).isNull();
        assertThat(relue.conduiteATenir()).isNull();
    }

    @Test
    void une_serologie_est_cherchee_par_identifiant_patient_et_centre() {
        var s = serologie(CENTRE_A, PATIENT, MarqueurSerologique.TPHA, LocalDate.of(2026, 1, 5), T0);
        serologies().ajouter(s);
        assertThat(serologies().trouverEnVerrouillant(CENTRE_B, PATIENT, s.id())).isEmpty();
        assertThat(serologies().trouverEnVerrouillant(CENTRE_A, AUTRE_PATIENT, s.id())).isEmpty();
        assertThat(serologies().trouverEnVerrouillant(CENTRE_A, PATIENT, UUID.randomUUID())).isEmpty();
    }

    @Test
    void la_correction_ne_change_que_le_resultat_la_conduite_et_la_date_de_modification() {
        var s = Serologie.enregistrer(UUID.randomUUID(), PATIENT, CENTRE_A, MarqueurSerologique.AG_HBS, ResultatSerologique.POSITIF,
                new BigDecimal("1.25"), "UI", LocalDate.of(2026, 1, 5), AUJOURDHUI, "Labo", null, "Isoler", T0);
        serologies().ajouter(s);
        var chargee = serologies().trouverEnVerrouillant(CENTRE_A, PATIENT, s.id()).orElseThrow();
        chargee.corrigerLeResultat(ResultatSerologique.NEGATIF, null, T0.plusSeconds(60));
        serologies().enregistrerLaCorrection(chargee);
        var relue = serologies().trouverEnVerrouillant(CENTRE_A, PATIENT, s.id()).orElseThrow();
        assertThat(relue.resultat()).isEqualTo(ResultatSerologique.NEGATIF);
        assertThat(relue.conduiteATenir()).isNull();
        assertThat(relue.modifieeLe()).isEqualTo(T0.plusSeconds(60));
        assertThat(relue.creeeLe()).isEqualTo(T0);
        assertThat(relue.titre()).isEqualByComparingTo("1.25");
        assertThat(relue.laboratoire()).isEqualTo("Labo");
        assertThat(relue.datePrelevement()).isEqualTo(LocalDate.of(2026, 1, 5));
    }

    @Test
    void la_suppression_dit_si_quelque_chose_a_ete_supprime_et_ne_touche_pas_les_autres() {
        var s = serologie(CENTRE_A, PATIENT, MarqueurSerologique.TPHA, LocalDate.of(2026, 1, 5), T0);
        var autre = serologie(CENTRE_A, PATIENT, MarqueurSerologique.AG_HBS, LocalDate.of(2026, 1, 5), T0);
        serologies().ajouter(s);
        serologies().ajouter(autre);
        assertThat(serologies().supprimer(CENTRE_B, PATIENT, s.id())).isFalse();
        assertThat(serologies().supprimer(CENTRE_A, AUTRE_PATIENT, s.id())).isFalse();
        assertThat(serologies().supprimer(CENTRE_A, PATIENT, UUID.randomUUID())).isFalse();
        assertThat(serologies().trouverEnVerrouillant(CENTRE_A, PATIENT, s.id())).isPresent();
        assertThat(serologies().supprimer(CENTRE_A, PATIENT, s.id())).isTrue();
        assertThat(serologies().supprimer(CENTRE_A, PATIENT, s.id())).isFalse();
        assertThat(serologies().trouverEnVerrouillant(CENTRE_A, PATIENT, s.id())).isEmpty();
        assertThat(serologies().trouverEnVerrouillant(CENTRE_A, PATIENT, autre.id())).isPresent();
    }

    @Test
    void l_existence_porte_sur_le_patient_le_centre_le_marqueur_et_la_date() {
        serologies().ajouter(serologie(CENTRE_A, PATIENT, MarqueurSerologique.TPHA, LocalDate.of(2026, 1, 5), T0));
        assertThat(serologies().existeEnVerrouillant(CENTRE_A, PATIENT, MarqueurSerologique.TPHA, LocalDate.of(2026, 1, 5))).isTrue();
        assertThat(serologies().existeEnVerrouillant(CENTRE_A, PATIENT, MarqueurSerologique.TPHA, LocalDate.of(2026, 1, 6))).isFalse();
        assertThat(serologies().existeEnVerrouillant(CENTRE_A, PATIENT, MarqueurSerologique.AG_HBS, LocalDate.of(2026, 1, 5))).isFalse();
        assertThat(serologies().existeEnVerrouillant(CENTRE_A, AUTRE_PATIENT, MarqueurSerologique.TPHA, LocalDate.of(2026, 1, 5))).isFalse();
        assertThat(serologies().existeEnVerrouillant(CENTRE_B, PATIENT, MarqueurSerologique.TPHA, LocalDate.of(2026, 1, 5))).isFalse();
    }

    @Test
    void la_liste_est_triee_de_la_date_la_plus_recente_a_la_plus_ancienne_et_paginee_par_patient_et_centre() {
        for (int jour = 1; jour <= 5; jour++) {
            serologies().ajouter(serologie(CENTRE_A, PATIENT, MarqueurSerologique.TPHA, LocalDate.of(2026, 1, jour), T0));
        }
        serologies().ajouter(serologie(CENTRE_A, AUTRE_PATIENT, MarqueurSerologique.TPHA, LocalDate.of(2026, 1, 9), T0));
        serologies().ajouter(serologie(CENTRE_B, PATIENT, MarqueurSerologique.TPHA, LocalDate.of(2026, 1, 9), T0));
        var premiere = serologies().duPatient(CENTRE_A, PATIENT, 0, 2);
        assertThat(premiere.total()).isEqualTo(5);
        assertThat(premiere.items()).extracting(Serologie::datePrelevement).containsExactly(LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 4));
        var derniere = serologies().duPatient(CENTRE_A, PATIENT, 2, 2);
        assertThat(derniere.items()).extracting(Serologie::datePrelevement).containsExactly(LocalDate.of(2026, 1, 1));
        assertThat(serologies().duPatient(CENTRE_A, PATIENT, 9, 2).items()).isEmpty();
        assertThat(serologies().duPatient(CENTRE_A, new PatientId(UUID.randomUUID()), 0, 2).total()).isZero();
        assertThat(serologies().toutesDuPatient(CENTRE_A, PATIENT)).hasSize(5);
    }

    @Test
    void a_date_egale_la_derniere_creee_vient_en_premier() {
        var premiere = serologie(CENTRE_A, PATIENT, MarqueurSerologique.TPHA, LocalDate.of(2026, 1, 5), T0);
        var seconde = serologie(CENTRE_A, PATIENT, MarqueurSerologique.AG_HBS, LocalDate.of(2026, 1, 5), T0.plusSeconds(5));
        serologies().ajouter(premiere);
        serologies().ajouter(seconde);
        assertThat(serologies().duPatient(CENTRE_A, PATIENT, 0, 10).items()).extracting(Serologie::id).containsExactly(seconde.id(), premiere.id());
    }
}
