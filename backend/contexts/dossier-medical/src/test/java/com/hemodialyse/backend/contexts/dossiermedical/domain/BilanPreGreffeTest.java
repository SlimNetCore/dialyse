package com.hemodialyse.backend.contexts.dossiermedical.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Invariants du bilan pré-greffe : machine à états reproduite (KB-MED-92), bornes de colonne (400), décisions de RCP. */
class BilanPreGreffeTest {
    private static final Instant T0 = Instant.parse("2026-11-07T10:00:00Z");
    private static final LocalDate JOUR = LocalDate.of(2026, 11, 7);

    private static BilanPreGreffe bilan() {
        return BilanPreGreffe.ouvrir(UUID.randomUUID(), new PatientId(UUID.randomUUID()), new CentreId(UUID.randomUUID()), T0);
    }

    private static void code(Runnable action, String code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(RegleDeGestionViolee.class, e -> assertThat(e.code()).isEqualTo(code));
    }

    @Test
    void un_bilan_ouvert_est_non_debute_et_vierge() {
        var b = bilan();
        assertThat(b.statut()).isEqualTo(StatutDuBilanPreGreffe.NON_DEBUTE);
        assertThat(b.dateDebutBilan()).isNull();
        assertThat(b.decisions()).isEmpty();
        assertThat(b.creeLe()).isEqualTo(T0);
    }

    @Test
    void le_debut_du_bilan_pose_la_date_de_debut_une_seule_fois() {
        var b = bilan();
        b.changerLeStatut(StatutDuBilanPreGreffe.BILAN_EN_COURS, JOUR, T0);
        b.changerLeStatut(StatutDuBilanPreGreffe.CONTRE_INDICATION_TEMPORAIRE, JOUR.plusDays(1), T0);
        b.changerLeStatut(StatutDuBilanPreGreffe.BILAN_EN_COURS, JOUR.plusDays(2), T0);
        assertThat(b.dateDebutBilan()).isEqualTo(JOUR);
    }

    @Test
    void l_inscription_n_est_possible_que_depuis_eligible_et_date_l_inscription_a_chaque_appel() {
        var b = bilan();
        code(() -> b.changerLeStatut(StatutDuBilanPreGreffe.INSCRIT_LISTE_ATTENTE, JOUR, T0), "BILAN_GREFFE_TRANSITION_INVALIDE");
        b.changerLeStatut(StatutDuBilanPreGreffe.BILAN_EN_COURS, JOUR, T0);
        b.changerLeStatut(StatutDuBilanPreGreffe.ELIGIBLE, JOUR, T0);
        b.changerLeStatut(StatutDuBilanPreGreffe.INSCRIT_LISTE_ATTENTE, JOUR, T0);
        b.changerLeStatut(StatutDuBilanPreGreffe.INSCRIT_LISTE_ATTENTE, JOUR.plusDays(3), T0);
        assertThat(b.dateInscriptionListeAttente()).isEqualTo(JOUR.plusDays(3));
    }

    @Test
    void les_statuts_terminaux_ne_permettent_que_de_rester_dans_le_meme_statut() {
        var b = bilan();
        b.changerLeStatut(StatutDuBilanPreGreffe.CONTRE_INDICATION_DEFINITIVE, JOUR, T0);
        b.changerLeStatut(StatutDuBilanPreGreffe.CONTRE_INDICATION_DEFINITIVE, JOUR, T0);
        code(() -> b.changerLeStatut(StatutDuBilanPreGreffe.BILAN_EN_COURS, JOUR, T0), "BILAN_GREFFE_TRANSITION_INVALIDE");
    }

    @Test
    void la_greffe_realisee_date_la_greffe() {
        var b = BilanPreGreffe.reconstituer(UUID.randomUUID(), new PatientId(UUID.randomUUID()), new CentreId(UUID.randomUUID()),
                StatutDuBilanPreGreffe.INSCRIT_LISTE_ATTENTE, null, null, null, null, null, null, null, null, null, java.util.List.of(), T0, T0);
        b.changerLeStatut(StatutDuBilanPreGreffe.GREFFE_REALISEE, JOUR, T0);
        assertThat(b.dateGreffe()).isEqualTo(JOUR);
    }

    @Test
    void un_statut_absent_est_refuse() {
        code(() -> bilan().changerLeStatut(null, JOUR, T0), "BILAN_GREFFE_STATUT_REQUIS");
    }

    @Test
    void le_bilan_immunologique_remplace_les_quatre_champs_et_efface_les_absents() {
        var b = bilan();
        b.mettreAJourLeBilanImmunologique("A+", "A1", new BigDecimal("12.5"), new BigDecimal("80"), T0);
        b.mettreAJourLeBilanImmunologique("O-", null, null, null, T0);
        assertThat(b.groupeSanguinConfirme()).isEqualTo("O-");
        assertThat(b.typageHla()).isNull();
        assertThat(b.praClasseI()).isNull();
    }

    @Test
    void les_bornes_de_colonne_sont_refusees_en_saisie_invalide() {
        var b = bilan();
        assertThatThrownBy(() -> b.mettreAJourLeBilanImmunologique("ABCDEF", null, null, null, T0)).isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> b.mettreAJourLeBilanImmunologique(null, "x".repeat(501), null, null, T0)).isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> b.mettreAJourLeBilanImmunologique(null, null, new BigDecimal("1000"), null, T0)).isInstanceOf(SaisieInvalide.class);
        b.mettreAJourLeBilanImmunologique("ABCDE", "x".repeat(500), new BigDecimal("999.99"), new BigDecimal("-5"), T0);
        b.mettreAJourLeBilanImmunologique(null, null, new BigDecimal("999.994"), new BigDecimal("100.01"), T0);
        assertThatThrownBy(() -> b.mettreAJourLeBilanImmunologique(null, null, new BigDecimal("999.995"), null, T0)).isInstanceOf(SaisieInvalide.class);
    }

    @Test
    void les_notes_remplacent_les_deux_textes() {
        var b = bilan();
        b.mettreAJourLesNotes("a", "b", T0);
        b.mettreAJourLesNotes(null, "c", T0.plusSeconds(1));
        assertThat(b.contreIndications()).isNull();
        assertThat(b.conclusionNephrologue()).isEqualTo("c");
        assertThat(b.modifieLe()).isEqualTo(T0.plusSeconds(1));
    }

    @Test
    void une_decision_exige_une_date_de_reunion_non_future_et_un_avis() {
        var b = bilan();
        code(() -> b.ajouterUneDecision(UUID.randomUUID(), JOUR.plusDays(1), AvisRcp.FAVORABLE, null, null, JOUR, T0), "DECISION_RCP_DATE_FUTURE");
        code(() -> b.ajouterUneDecision(UUID.randomUUID(), null, AvisRcp.FAVORABLE, null, null, JOUR, T0), "DECISION_RCP_CHAMPS_REQUIS");
        code(() -> b.ajouterUneDecision(UUID.randomUUID(), JOUR, null, null, null, JOUR, T0), "DECISION_RCP_CHAMPS_REQUIS");
        assertThat(b.decisions()).isEmpty();
        b.ajouterUneDecision(UUID.randomUUID(), JOUR, AvisRcp.AJOURNE, "cr", JOUR.minusDays(5), JOUR, T0);
        b.ajouterUneDecision(UUID.randomUUID(), JOUR, AvisRcp.AJOURNE, "cr", JOUR.plusYears(3), JOUR, T0);
        assertThat(b.decisions()).hasSize(2);
    }
}
