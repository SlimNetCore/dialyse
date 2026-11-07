package com.hemodialyse.backend.contexts.dossiermedical.contrat;

import com.hemodialyse.backend.contexts.dossiermedical.domain.Analyse;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Analyses;
import com.hemodialyse.backend.contexts.dossiermedical.domain.MesuresDuBilan;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Periode;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Contrat du port {@link Analyses} : le double en mémoire, H2 et PostgreSQL 16 passent le MÊME jeu de tests. */
abstract class AnalysesContrat {
    static final CentreId CENTRE_A = new CentreId(UUID.fromString("00000000-0000-0000-0000-00000000d6a1"));
    static final CentreId CENTRE_B = new CentreId(UUID.fromString("00000000-0000-0000-0000-00000000d6b2"));
    static final PatientId PATIENT = new PatientId(UUID.fromString("00000000-0000-0000-0000-0000000d6001"));
    static final PatientId AUTRE_PATIENT = new PatientId(UUID.fromString("00000000-0000-0000-0000-0000000d6002"));
    static final Instant T0 = Instant.parse("2026-01-01T10:00:00Z");
    static final LocalDate AUJOURDHUI = LocalDate.of(2026, 6, 15);

    protected abstract Analyses analyses();

    static MesuresDuBilan hemoglobine(String valeur) {
        return new MesuresDuBilan(new BigDecimal(valeur), null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    static MesuresDuBilan toutesLesMesures() {
        return new MesuresDuBilan(new BigDecimal("11.50"), new BigDecimal("34.00"), 220, new BigDecimal("450.00"), new BigDecimal("30.00"),
                new BigDecimal("12.00"), new BigDecimal("120.00"), new BigDecimal("40.00"), new BigDecimal("8.50"), new BigDecimal("1.40"),
                new BigDecimal("5.20"), new BigDecimal("9.10"), new BigDecimal("300.00"), new BigDecimal("3.80"), new BigDecimal("6.90"),
                new BigDecimal("4.00"));
    }

    static Analyse analyse(CentreId centre, PatientId patient, LocalDate prelevement, String hemoglobine, Instant creee) {
        return Analyse.enregistrer(UUID.randomUUID(), patient, centre, prelevement, hemoglobine(hemoglobine), AUJOURDHUI, creee);
    }

    @Test
    void une_analyse_ajoutee_est_relue_avec_ses_seize_mesures() {
        var a = Analyse.enregistrer(UUID.randomUUID(), PATIENT, CENTRE_A, LocalDate.of(2026, 1, 5), toutesLesMesures(), AUJOURDHUI, T0);
        analyses().ajouter(a);
        var relue = analyses().trouverEnVerrouillant(CENTRE_A, PATIENT, a.id()).orElseThrow();
        assertThat(relue.datePrelevement()).isEqualTo(LocalDate.of(2026, 1, 5));
        assertThat(relue.mesures()).usingRecursiveComparison().withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .isEqualTo(toutesLesMesures());
        assertThat(relue.creeLe()).isEqualTo(T0);
        assertThat(relue.modifieLe()).isEqualTo(T0);
        assertThat(relue.centre()).isEqualTo(CENTRE_A);
        assertThat(relue.patient()).isEqualTo(PATIENT);
    }

    @Test
    void une_analyse_est_cherchee_par_identifiant_patient_et_centre() {
        var a = analyse(CENTRE_A, PATIENT, LocalDate.of(2026, 1, 5), "10", T0);
        analyses().ajouter(a);
        assertThat(analyses().trouverEnVerrouillant(CENTRE_B, PATIENT, a.id())).isEmpty();
        assertThat(analyses().trouverEnVerrouillant(CENTRE_A, AUTRE_PATIENT, a.id())).isEmpty();
        assertThat(analyses().trouverEnVerrouillant(CENTRE_A, PATIENT, UUID.randomUUID())).isEmpty();
    }

    @Test
    void le_remplacement_efface_les_mesures_absentes_et_reinitialise_la_creation() {
        var a = Analyse.enregistrer(UUID.randomUUID(), PATIENT, CENTRE_A, LocalDate.of(2026, 1, 5), toutesLesMesures(), AUJOURDHUI, T0);
        analyses().ajouter(a);
        var chargee = analyses().trouverEnVerrouillant(CENTRE_A, PATIENT, a.id()).orElseThrow();
        chargee.remplacer(LocalDate.of(2026, 1, 8), hemoglobine("9.00"), AUJOURDHUI, T0.plusSeconds(60));
        analyses().enregistrerLeRemplacement(chargee);
        var relue = analyses().trouverEnVerrouillant(CENTRE_A, PATIENT, a.id()).orElseThrow();
        assertThat(relue.datePrelevement()).isEqualTo(LocalDate.of(2026, 1, 8));
        assertThat(relue.mesures().hemoglobine()).isEqualByComparingTo("9.00");
        assertThat(relue.mesures().hematocrite()).isNull();
        assertThat(relue.mesures().plaquettes()).isNull();
        assertThat(relue.mesures().crp()).isNull();
        assertThat(relue.creeLe()).isEqualTo(T0.plusSeconds(60));
        assertThat(relue.modifieLe()).isEqualTo(T0.plusSeconds(60));
        assertThat(relue.patient()).isEqualTo(PATIENT);
        assertThat(relue.centre()).isEqualTo(CENTRE_A);
    }

    @Test
    void la_suppression_dit_si_quelque_chose_a_ete_supprime_et_ne_touche_pas_les_autres() {
        var a = analyse(CENTRE_A, PATIENT, LocalDate.of(2026, 1, 5), "10", T0);
        var autre = analyse(CENTRE_A, PATIENT, LocalDate.of(2026, 1, 6), "11", T0);
        analyses().ajouter(a);
        analyses().ajouter(autre);
        assertThat(analyses().supprimer(CENTRE_B, PATIENT, a.id())).isFalse();
        assertThat(analyses().supprimer(CENTRE_A, AUTRE_PATIENT, a.id())).isFalse();
        assertThat(analyses().supprimer(CENTRE_A, PATIENT, UUID.randomUUID())).isFalse();
        assertThat(analyses().supprimer(CENTRE_A, PATIENT, a.id())).isTrue();
        assertThat(analyses().supprimer(CENTRE_A, PATIENT, a.id())).isFalse();
        assertThat(analyses().trouverEnVerrouillant(CENTRE_A, PATIENT, autre.id())).isPresent();
    }

    @Test
    void plusieurs_analyses_le_meme_jour_sont_permises() {
        analyses().ajouter(analyse(CENTRE_A, PATIENT, LocalDate.of(2026, 1, 5), "10", T0));
        analyses().ajouter(analyse(CENTRE_A, PATIENT, LocalDate.of(2026, 1, 5), "11", T0));
        assertThat(analyses().duPatient(CENTRE_A, PATIENT, Periode.ILLIMITEE, 0, 10).total()).isEqualTo(2);
    }

    @Test
    void la_liste_est_triee_du_prelevement_le_plus_recent_au_plus_ancien_et_paginee_par_patient_et_centre() {
        for (int jour = 1; jour <= 5; jour++) {
            analyses().ajouter(analyse(CENTRE_A, PATIENT, LocalDate.of(2026, 1, jour), "10", T0));
        }
        analyses().ajouter(analyse(CENTRE_A, AUTRE_PATIENT, LocalDate.of(2026, 1, 9), "10", T0));
        analyses().ajouter(analyse(CENTRE_B, PATIENT, LocalDate.of(2026, 1, 9), "10", T0));
        var premiere = analyses().duPatient(CENTRE_A, PATIENT, Periode.ILLIMITEE, 0, 2);
        assertThat(premiere.total()).isEqualTo(5);
        assertThat(premiere.items()).extracting(Analyse::datePrelevement).containsExactly(LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 4));
        assertThat(analyses().duPatient(CENTRE_A, PATIENT, Periode.ILLIMITEE, 2, 2).items()).extracting(Analyse::datePrelevement)
                .containsExactly(LocalDate.of(2026, 1, 1));
        assertThat(analyses().duPatient(CENTRE_A, PATIENT, Periode.ILLIMITEE, 9, 2).items()).isEmpty();
        assertThat(analyses().duPatient(CENTRE_A, new PatientId(UUID.randomUUID()), Periode.ILLIMITEE, 0, 2).total()).isZero();
    }

    @Test
    void a_prelevement_egal_la_derniere_creee_vient_en_premier() {
        var premiere = analyse(CENTRE_A, PATIENT, LocalDate.of(2026, 1, 5), "10", T0);
        var seconde = analyse(CENTRE_A, PATIENT, LocalDate.of(2026, 1, 5), "11", T0.plusSeconds(5));
        analyses().ajouter(premiere);
        analyses().ajouter(seconde);
        assertThat(analyses().duPatient(CENTRE_A, PATIENT, Periode.ILLIMITEE, 0, 10).items()).extracting(Analyse::id)
                .containsExactly(seconde.id(), premiere.id());
    }

    @Test
    void la_periode_a_ses_bornes_incluses_et_facultatives() {
        for (int jour : new int[]{1, 5, 9}) {
            analyses().ajouter(analyse(CENTRE_A, PATIENT, LocalDate.of(2026, 1, jour), "10", T0));
        }
        assertThat(analyses().duPatient(CENTRE_A, PATIENT, new Periode(LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 9)), 0, 10).total()).isEqualTo(2);
        assertThat(analyses().duPatient(CENTRE_A, PATIENT, new Periode(LocalDate.of(2026, 1, 5), null), 0, 10).total()).isEqualTo(2);
        assertThat(analyses().duPatient(CENTRE_A, PATIENT, new Periode(null, LocalDate.of(2026, 1, 5)), 0, 10).total()).isEqualTo(2);
        assertThat(analyses().duPatient(CENTRE_A, PATIENT, new Periode(LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 5)), 0, 10).total()).isEqualTo(1);
    }
}
