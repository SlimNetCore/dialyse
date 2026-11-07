package com.hemodialyse.backend.contexts.dossiermedical.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Les règles de la sérologie : positivité, date, bornes, correction, statut sérologique. Domaine pur, sans mock. */
class SerologieTest {

    private static final PatientId PATIENT = new PatientId(UUID.fromString("00000000-0000-0000-0000-0000000000a1"));
    private static final CentreId CENTRE = new CentreId(UUID.fromString("00000000-0000-0000-0000-00000000c001"));
    private static final LocalDate AUJOURDHUI = LocalDate.of(2026, 6, 15);
    private static final Instant T0 = Instant.parse("2026-06-15T10:00:00Z");

    private static Serologie enregistrer(MarqueurSerologique marqueur, ResultatSerologique resultat, String conduite) {
        return Serologie.enregistrer(UUID.randomUUID(), PATIENT, CENTRE, marqueur, resultat, null, null, AUJOURDHUI, AUJOURDHUI, null, null,
                conduite, T0);
    }

    private static Serologie lue(MarqueurSerologique marqueur, LocalDate date, Instant creeeLe) {
        return Serologie.reconstituer(UUID.randomUUID(), PATIENT, CENTRE, marqueur, ResultatSerologique.NEGATIF, null, null, date, null, null,
                null, creeeLe, creeeLe);
    }

    @ParameterizedTest
    @EnumSource(MarqueurSerologique.class)
    void un_resultat_positif_sans_conduite_est_refuse_pour_chaque_marqueur(MarqueurSerologique marqueur) {
        assertThatThrownBy(() -> enregistrer(marqueur, ResultatSerologique.POSITIF, null))
                .isInstanceOfSatisfying(RegleDeGestionViolee.class, e -> assertThat(e.code()).isEqualTo("SEROLOGIE_CONDUITE_REQUISE"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void une_conduite_blanche_equivaut_a_une_conduite_absente_pour_un_resultat_positif(String blanche) {
        assertThatThrownBy(() -> enregistrer(MarqueurSerologique.VIH_AC, ResultatSerologique.POSITIF, blanche))
                .isInstanceOf(RegleDeGestionViolee.class);
    }

    @ParameterizedTest
    @EnumSource(value = ResultatSerologique.class, names = {"NEGATIF", "DOUTEUX", "EN_COURS"})
    void les_autres_resultats_s_enregistrent_sans_conduite(ResultatSerologique resultat) {
        assertThat(enregistrer(MarqueurSerologique.TPHA, resultat, null).conduiteATenir()).isNull();
        assertThat(resultat.exigeUneConduiteATenir()).isFalse();
    }

    @Test
    void un_resultat_positif_avec_conduite_est_enregistre_tel_quel() {
        var s = enregistrer(MarqueurSerologique.AG_HBS, ResultatSerologique.POSITIF, "Isoler");
        assertThat(s.resultat()).isEqualTo(ResultatSerologique.POSITIF);
        assertThat(s.conduiteATenir()).isEqualTo("Isoler");
        assertThat(s.creeeLe()).isEqualTo(T0).isEqualTo(s.modifieeLe());
    }

    @Test
    void une_date_de_prelevement_future_est_refusee_et_celle_du_jour_acceptee() {
        assertThatThrownBy(() -> Serologie.enregistrer(UUID.randomUUID(), PATIENT, CENTRE, MarqueurSerologique.TPHA, ResultatSerologique.NEGATIF,
                null, null, AUJOURDHUI.plusDays(1), AUJOURDHUI, null, null, null, T0))
                .isInstanceOfSatisfying(RegleDeGestionViolee.class, e -> assertThat(e.code()).isEqualTo("SEROLOGIE_DATE_FUTURE"));
        assertThat(Serologie.enregistrer(UUID.randomUUID(), PATIENT, CENTRE, MarqueurSerologique.TPHA, ResultatSerologique.NEGATIF, null, null,
                AUJOURDHUI, AUJOURDHUI, null, null, null, T0).datePrelevement()).isEqualTo(AUJOURDHUI);
    }

    @Test
    void une_tres_ancienne_date_et_un_prochain_controle_anterieur_sont_acceptes_comme_le_legacy() {
        var s = Serologie.enregistrer(UUID.randomUUID(), PATIENT, CENTRE, MarqueurSerologique.TPHA, ResultatSerologique.NEGATIF, null, null,
                LocalDate.of(1900, 1, 1), AUJOURDHUI, null, LocalDate.of(1899, 1, 1), null, T0);
        assertThat(s.datePrelevement()).isEqualTo(LocalDate.of(1900, 1, 1));
    }

    @Test
    void les_champs_obligatoires_sont_controles() {
        assertThatThrownBy(() -> Serologie.enregistrer(UUID.randomUUID(), PATIENT, CENTRE, null, ResultatSerologique.NEGATIF, null, null,
                AUJOURDHUI, AUJOURDHUI, null, null, null, T0)).isInstanceOf(RegleDeGestionViolee.class);
    }

    @Test
    void le_titre_est_arrondi_a_trois_decimales_comme_la_colonne_et_garde_sa_forme_sinon() {
        assertThat(avecTitre("12.3456").titre()).isEqualByComparingTo("12.346").extracting(BigDecimal::scale).isEqualTo(3);
        assertThat(avecTitre("1.0").titre().scale()).isEqualTo(1);
        assertThat(avecTitre("-4.5").titre()).isEqualByComparingTo("-4.5");
    }

    @Test
    void le_titre_est_limite_a_sept_chiffres_entiers_apres_arrondi() {
        assertThat(avecTitre("9999999.999").titre()).isEqualByComparingTo("9999999.999");
        assertThatThrownBy(() -> avecTitre("10000000")).isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> avecTitre("9999999.9996")).isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> avecTitre("-10000000")).isInstanceOf(SaisieInvalide.class);
    }

    private static Serologie avecTitre(String titre) {
        return Serologie.enregistrer(UUID.randomUUID(), PATIENT, CENTRE, MarqueurSerologique.TPHA, ResultatSerologique.NEGATIF,
                new BigDecimal(titre), null, AUJOURDHUI, AUJOURDHUI, null, null, null, T0);
    }

    @Test
    void l_unite_et_le_laboratoire_sont_bornes() {
        assertThat(avecTexte("x".repeat(20), "y".repeat(255)).unite()).hasSize(20);
        assertThatThrownBy(() -> avecTexte("x".repeat(21), null)).isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> avecTexte(null, "y".repeat(256))).isInstanceOf(SaisieInvalide.class);
    }

    private static Serologie avecTexte(String unite, String laboratoire) {
        return Serologie.enregistrer(UUID.randomUUID(), PATIENT, CENTRE, MarqueurSerologique.TPHA, ResultatSerologique.NEGATIF, null, unite,
                AUJOURDHUI, AUJOURDHUI, laboratoire, null, null, T0);
    }

    @Test
    void la_conduite_a_tenir_n_a_pas_de_limite() {
        assertThat(enregistrer(MarqueurSerologique.TPHA, ResultatSerologique.NEGATIF, "c".repeat(5000)).conduiteATenir()).hasSize(5000);
    }

    @Test
    void corriger_vers_positif_exige_une_conduite_et_la_correction_remplace_la_conduite_meme_absente() {
        var s = enregistrer(MarqueurSerologique.AG_HBS, ResultatSerologique.POSITIF, "Isoler");
        assertThatThrownBy(() -> s.corrigerLeResultat(ResultatSerologique.POSITIF, null, T0.plusSeconds(60)))
                .isInstanceOf(RegleDeGestionViolee.class);
        assertThat(s.modifieeLe()).isEqualTo(T0);
        s.corrigerLeResultat(ResultatSerologique.NEGATIF, null, T0.plusSeconds(60));
        assertThat(s.resultat()).isEqualTo(ResultatSerologique.NEGATIF);
        assertThat(s.conduiteATenir()).isNull();
        assertThat(s.modifieeLe()).isEqualTo(T0.plusSeconds(60));
        assertThat(s.creeeLe()).isEqualTo(T0);
    }

    @Test
    void la_lecture_d_un_marqueur_ou_d_un_resultat_inconnu_ne_revele_aucun_nom_interne() {
        for (String inconnu : new String[]{"", "vih_ac", "HIV", "TOXO"}) {
            assertThatThrownBy(() -> MarqueurSerologique.lire(inconnu)).isInstanceOf(SaisieInvalide.class)
                    .hasMessageNotContaining("com.hemodialyse").hasMessageNotContaining("No enum constant");
        }
        for (String inconnu : new String[]{"", "negatif", "POSITIVE", "INDETERMINE"}) {
            assertThatThrownBy(() -> ResultatSerologique.lire(inconnu)).isInstanceOf(SaisieInvalide.class)
                    .hasMessageNotContaining("com.hemodialyse");
        }
        assertThatThrownBy(() -> MarqueurSerologique.lire(null)).isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> ResultatSerologique.lire(null)).isInstanceOf(SaisieInvalide.class);
        assertThat(MarqueurSerologique.values()).hasSize(13);
        assertThat(MarqueurSerologique.lire("VIH_AC")).isEqualTo(MarqueurSerologique.VIH_AC);
        assertThat(ResultatSerologique.lire("EN_COURS")).isEqualTo(ResultatSerologique.EN_COURS);
    }

    @Test
    void le_statut_est_le_dernier_resultat_par_marqueur_dans_l_ordre_des_marqueurs() {
        var ancienne = lue(MarqueurSerologique.AG_HBS, LocalDate.of(2026, 1, 1), T0);
        var recente = lue(MarqueurSerologique.AG_HBS, LocalDate.of(2026, 3, 1), T0);
        var autre = lue(MarqueurSerologique.VIH_AC, LocalDate.of(2025, 1, 1), T0);
        assertThat(StatutSerologique.derniersResultats(List.of(ancienne, recente, autre))).containsExactly(autre, recente);
        assertThat(StatutSerologique.derniersResultats(List.of(recente, ancienne, autre))).containsExactly(autre, recente);
        assertThat(StatutSerologique.derniersResultats(List.of())).isEmpty();
    }

    @Test
    void a_date_egale_le_statut_retient_le_dernier_cree() {
        var premiere = lue(MarqueurSerologique.TPHA, LocalDate.of(2026, 1, 1), T0);
        var seconde = lue(MarqueurSerologique.TPHA, LocalDate.of(2026, 1, 1), T0.plusSeconds(5));
        assertThat(StatutSerologique.derniersResultats(List.of(premiere, seconde))).containsExactly(seconde);
        assertThat(StatutSerologique.derniersResultats(List.of(seconde, premiere))).containsExactly(seconde);
    }
}
