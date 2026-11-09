package com.hemodialyse.backend.contexts.pilotagedirection.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LaPeriodeEtLeSeuilDAnonymatTest {

    private static final LocalDate AUJOURDHUI = LocalDate.of(2026, 11, 9);

    @Test
    void une_periode_sans_bornes_va_du_premier_janvier_a_aujourd_hui() {
        var periode = Periode.demandee(null, null, AUJOURDHUI);

        assertThat(periode.debut()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(periode.fin()).isEqualTo(AUJOURDHUI);
    }

    @Test
    void un_debut_absent_vaut_le_premier_janvier_de_l_annee_de_la_fin() {
        var periode = Periode.demandee(null, LocalDate.of(2025, 6, 30), AUJOURDHUI);

        assertThat(periode.debut()).isEqualTo(LocalDate.of(2025, 1, 1));
    }

    @Test
    void un_debut_apres_la_fin_est_refuse() {
        assertThatThrownBy(() -> Periode.demandee(LocalDate.of(2026, 5, 2), LocalDate.of(2026, 5, 1), AUJOURDHUI))
                .isInstanceOfSatisfying(RegleDuPilotageViolee.class, e -> assertThat(e.code()).isEqualTo("PERIODE_INVALIDE"));
    }

    @Test
    void cinq_ans_pile_sont_acceptes_et_cinq_ans_et_un_jour_refuses() {
        assertThat(Periode.demandee(LocalDate.of(2021, 1, 1), LocalDate.of(2026, 1, 1), AUJOURDHUI)).isNotNull();
        assertThatThrownBy(() -> Periode.demandee(LocalDate.of(2021, 1, 1), LocalDate.of(2026, 1, 2), AUJOURDHUI))
                .isInstanceOfSatisfying(RegleDuPilotageViolee.class, e -> assertThat(e.code()).isEqualTo("PERIODE_TROP_LONGUE"));
    }

    @Test
    void la_periode_precedente_a_la_meme_duree_et_la_precede_immediatement() {
        var precedente = new Periode(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)).precedente();

        assertThat(precedente.fin()).isEqualTo(LocalDate.of(2026, 2, 28));
        assertThat(precedente.debut()).isEqualTo(LocalDate.of(2026, 1, 29));
    }

    @Test
    void la_periode_precedente_n_est_pas_soumise_a_la_limite_de_cinq_ans() {
        var precedente = new Periode(LocalDate.of(2021, 1, 1), LocalDate.of(2026, 1, 1)).precedente();

        assertThat(precedente.fin()).isEqualTo(LocalDate.of(2020, 12, 31));
    }

    @Test
    void un_effectif_entre_un_et_quatre_est_masque_et_zero_reste_zero() {
        assertThat(SeuilDAnonymat.masquer(0)).isEqualTo(0L);
        assertThat(SeuilDAnonymat.masquer(1)).isNull();
        assertThat(SeuilDAnonymat.masquer(4)).isNull();
        assertThat(SeuilDAnonymat.masquer(5)).isEqualTo(5L);
    }

    private static BilanDuCentre bilan(String nom, long patients, long sousKt) {
        return new BilanDuCentre(UUID.randomUUID(), nom, true, patients, sousKt, 10, 2, ChiffresFinanciers.zero());
    }

    @Test
    void le_total_de_la_societe_est_supprime_quand_tous_ses_centres_sont_masques_kb_dir_01_corrige() {
        // KB-DIR-01 corrige (2026-11-09) : 3 + 4 patients masques dans les centres, total supprime (plus de fuite par difference).
        var a = new BilanDuCentre(UUID.randomUUID(), "A", true, 3, 1, 10, 2, ChiffresFinanciers.zero());
        var b = new BilanDuCentre(UUID.randomUUID(), "B", true, 4, 2, 5, 1, ChiffresFinanciers.zero());

        var total = BilanDuCentre.consolider(List.of(a, b));

        assertThat(a.patientsPublies()).isNull();
        assertThat(b.patientsPublies()).isNull();
        assertThat(total.patientsPublies()).isNull();
        assertThat(total.patientsSousKtPublies()).isNull();
        assertThat(total.seances()).isEqualTo(15);
        assertThat(total.patients()).isEqualTo(7);
    }

    @Test
    void un_seul_centre_masque_suffit_a_supprimer_le_total_mais_pas_les_seances_ni_les_finances() {
        var grand = bilan("Grand", 50, 20);
        var petit = bilan("Petit", 3, 12);

        var total = BilanDuCentre.consolider(List.of(grand, petit));

        assertThat(grand.patientsPublies()).isEqualTo(50L);
        assertThat(petit.patientsPublies()).isNull();
        assertThat(total.patientsPublies()).isNull();
        assertThat(total.patientsSousKtPublies()).isEqualTo(32L);
        assertThat(total.seances()).isEqualTo(20);
    }

    @Test
    void le_total_est_publie_sans_centre_masque_a_cinq_pile_et_zero_reste_zero() {
        var cinq = bilan("Cinq", 5, 0);
        var zero = bilan("Zero", 0, 0);

        var total = BilanDuCentre.consolider(List.of(cinq, zero));

        assertThat(total.patientsPublies()).isEqualTo(5L);
        assertThat(total.patientsSousKtPublies()).isEqualTo(0L);
        assertThat(BilanDuCentre.consolider(List.of(bilan("Z", 0, 0))).patientsPublies()).isEqualTo(0L);
        assertThat(BilanDuCentre.sansActivite().patientsPublies()).isEqualTo(0L);
    }

    @Test
    void une_societe_a_un_seul_centre_masque_n_a_pas_de_total_et_une_societe_a_un_centre_publiable_garde_le_sien() {
        assertThat(BilanDuCentre.consolider(List.of(bilan("A", 2, 0))).patientsPublies()).isNull();
        assertThat(BilanDuCentre.consolider(List.of(bilan("A", 9, 0))).patientsPublies()).isEqualTo(9L);
    }

    @Test
    void la_suppression_complementaire_se_propage_a_un_total_de_totaux() {
        var consolide = BilanDuCentre.consolider(List.of(bilan("A", 3, 0), bilan("B", 40, 0)));

        assertThat(BilanDuCentre.consolider(List.of(consolide, bilan("C", 30, 0))).patientsPublies()).isNull();
    }

    @Test
    void la_somme_des_effectifs_publies_et_le_total_ne_permettent_plus_de_retrouver_un_effectif_masque() {
        var centres = List.of(bilan("A", 20, 0), bilan("B", 2, 0), bilan("C", 30, 0));

        var total = BilanDuCentre.consolider(centres);

        long sommeDesPublies = centres.stream().map(BilanDuCentre::patientsPublies).filter(java.util.Objects::nonNull)
                .mapToLong(Long::longValue).sum();
        assertThat(sommeDesPublies).isEqualTo(50L);
        assertThat(total.patientsPublies()).isNull();
    }

    @Test
    void un_total_deja_fige_est_supprime_a_la_lecture_si_un_centre_est_publie_masque() {
        assertThat(SeuilDAnonymat.totalPublieDepuis(7L, java.util.Arrays.asList(null, null))).isNull();
        assertThat(SeuilDAnonymat.totalPublieDepuis(57L, java.util.Arrays.asList(50L, null))).isNull();
        assertThat(SeuilDAnonymat.totalPublieDepuis(55L, java.util.Arrays.asList(50L, 5L))).isEqualTo(55L);
        assertThat(SeuilDAnonymat.totalPublieDepuis(0L, java.util.Arrays.asList(0L, 0L))).isEqualTo(0L);
        assertThat(SeuilDAnonymat.totalPublieDepuis(0L, List.of())).isEqualTo(0L);
        assertThat(SeuilDAnonymat.totalPublieDepuis(null, List.of(5L))).isNull();
    }

    @Test
    void le_reste_a_recouvrer_n_est_jamais_negatif_et_le_taux_est_arrondi_a_une_decimale() {
        var chiffres = new ChiffresFinanciers(new BigDecimal("100.00"), new BigDecimal("300.00"), new BigDecimal("100.00"));
        var trop_percu = new ChiffresFinanciers(new BigDecimal("1"), new BigDecimal("100.00"), new BigDecimal("150.00"));

        assertThat(chiffres.resteARecouvrer()).isEqualByComparingTo("200.00");
        assertThat(chiffres.tauxEncaissement()).isEqualByComparingTo("33.3");
        assertThat(trop_percu.resteARecouvrer()).isEqualByComparingTo("0");
        assertThat(ChiffresFinanciers.zero().tauxEncaissement()).isNull();
    }

    @Test
    void la_tranche_d_age_se_compte_au_jour_de_l_appel_et_une_naissance_future_est_inconnue() {
        assertThat(TrancheDAge.de(LocalDate.of(2008, 11, 9), AUJOURDHUI)).isEqualTo(TrancheDAge.DE_18_A_29);
        assertThat(TrancheDAge.de(LocalDate.of(2008, 11, 10), AUJOURDHUI)).isEqualTo(TrancheDAge.DE_0_A_17);
        assertThat(TrancheDAge.de(LocalDate.of(1966, 11, 9), AUJOURDHUI)).isEqualTo(TrancheDAge.SOIXANTE_ET_PLUS);
        assertThat(TrancheDAge.de(null, AUJOURDHUI)).isEqualTo(TrancheDAge.INCONNUE);
        assertThat(TrancheDAge.de(AUJOURDHUI.plusDays(1), AUJOURDHUI)).isEqualTo(TrancheDAge.INCONNUE);
    }

    @Test
    void un_code_d_alerte_inconnu_est_imprime_tel_quel() {
        assertThat(LibelleDAlerte.de("STOCK_SOUS_SEUIL")).isEqualTo("Articles sous le seuil d'alerte");
        assertThat(LibelleDAlerte.de("NOUVEAU_CODE")).isEqualTo("NOUVEAU_CODE");
    }
}
