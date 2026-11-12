package com.hemodialyse.backend.contexts.pilotagedirection.application;

import com.hemodialyse.backend.contexts.pilotagedirection.application.PageDuRapport.Genre;
import com.hemodialyse.backend.contexts.pilotagedirection.application.PageDuRapport.Ligne;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.RapportDeSyntheseDeLaDirection.Alerte;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.RapportDeSyntheseDeLaDirection.Caisses;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.RapportDeSyntheseDeLaDirection.LigneDeCaisse;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.RapportDeSyntheseDeLaDirection.Rapport;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.RapportDeSyntheseDeLaDirection.Repartitions;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.RapportDeSyntheseDeLaDirection.Synthese;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class MiseEnPageDuRapportTest {

    private static final ZoneId ALGER = ZoneId.of("Africa/Algiers");
    private static final String NBSP = " ";

    private static Rapport rapport(Synthese synthese, List<Alerte> alertes, Repartitions repartitions, OffsetDateTime genereLe) {
        return new Rapport("Rapport de direction", "Société", "2026-01-01", "2026-11-12", 5, genereLe, synthese, alertes, null, List.of(),
                null, null, repartitions);
    }

    private static Synthese synthese(BigDecimal caHt) {
        return new Synthese(null, 3L, 2L, caHt, caHt, caHt, caHt, new BigDecimal("38.4"));
    }

    @Test
    void les_montants_sont_arrondis_a_la_paire_sans_decimale_avec_un_separateur_insecable() {
        assertThat(MiseEnPageDuRapport.montant(new BigDecimal("2.50"))).isEqualTo("2" + NBSP + "DA");
        assertThat(MiseEnPageDuRapport.montant(new BigDecimal("3.50"))).isEqualTo("4" + NBSP + "DA");
        assertThat(MiseEnPageDuRapport.montant(new BigDecimal("0.50"))).isEqualTo("0" + NBSP + "DA");
        assertThat(MiseEnPageDuRapport.montant(new BigDecimal("1234567.4"))).isEqualTo("1" + NBSP + "234" + NBSP + "567" + NBSP + "DA");
        assertThat(MiseEnPageDuRapport.montant(new BigDecimal("-1500"))).isEqualTo("-1" + NBSP + "500" + NBSP + "DA");
        assertThat(MiseEnPageDuRapport.montant(null)).isEqualTo("—");
    }

    @Test
    void un_effectif_masque_s_imprime_sous_le_seuil_et_une_valeur_absente_par_un_tiret() {
        PageDuRapport page = MiseEnPageDuRapport.mettreEnPage(
                rapport(synthese(new BigDecimal("1000")), List.of(), null, OffsetDateTime.parse("2026-11-12T10:00:00Z")), null, ALGER);
        Ligne valeurs = page.lignes().stream().filter(l -> l.genre() == Genre.LIGNE).findFirst().orElseThrow();
        assertThat(valeurs.cellules().get(0)).isEqualTo("< seuil");
        assertThat(valeurs.cellules().get(7)).isEqualTo("38.4 %");
    }

    @Test
    void l_horodatage_est_celui_du_fuseau_d_alger() {
        OffsetDateTime minuitMoinsTrente = OffsetDateTime.of(2026, 11, 12, 23, 30, 0, 0, ZoneOffset.UTC);
        PageDuRapport page = MiseEnPageDuRapport.mettreEnPage(rapport(null, List.of(), null, minuitMoinsTrente), null, ALGER);
        assertThat(page.horodatage()).isEqualTo("13/11/2026 00:30");
        assertThat(page.contexte()).contains("période 2026-01-01 au 2026-11-12").contains("données au 13/11/2026 00:30");
    }

    @Test
    void une_alerte_critique_est_une_phrase_critique() {
        PageDuRapport page = MiseEnPageDuRapport.mettreEnPage(rapport(null, List.of(new Alerte("Centre A", "Stock bas", "CRITICAL",
                new BigDecimal("3"))), null, null), null, ALGER);
        assertThat(page.lignes()).singleElement().satisfies(l -> {
            assertThat(l.genre()).isEqualTo(Genre.TEXTE_CRITIQUE);
            assertThat(l.cellules()).containsExactly("Centre A — Stock bas (3)");
            assertThat(l.titreDeSection()).isEqualTo("Alertes");
        });
    }

    @Test
    void la_matrice_des_caisses_se_pagine_et_garde_la_colonne_total_societe() {
        int centres = 23;
        List<String> noms = IntStream.range(0, centres).mapToObj(i -> "Centre " + i).toList();
        List<Long> patients = new ArrayList<>(IntStream.range(0, centres).mapToObj(i -> (long) i).toList());
        List<BigDecimal> montants = new ArrayList<>(IntStream.range(0, centres).mapToObj(i -> BigDecimal.valueOf(i)).toList());
        LigneDeCaisse caisse = new LigneDeCaisse("CNAS", patients, 253L, patients, 253L, montants, BigDecimal.valueOf(253));
        Repartitions repartitions = new Repartitions(List.of(), List.of(), new Caisses(noms, List.of(caisse)), List.of());

        PageDuRapport page = MiseEnPageDuRapport.mettreEnPage(rapport(null, List.of(), repartitions, null), null, ALGER);

        List<Ligne> entetes = page.lignes().stream().filter(l -> l.genre() == Genre.ENTETE && l.cellules().get(0).endsWith("— caisse")).toList();
        assertThat(entetes).hasSize(9); // 3 mesures x ceil(23 / 10) tableaux
        assertThat(entetes).allSatisfy(e -> {
            assertThat(e.cellules()).hasSizeLessThanOrEqualTo(PageDuRapport.CELLULES_MAX);
            assertThat(e.cellules().get(e.cellules().size() - 1)).isEqualTo("Total société");
        });
        assertThat(page.lignes().stream().filter(l -> l.titreDeSection().equals("Caisses d'assurance par centre"))).hasSize(1);
    }

    @Test
    void une_ligne_de_plus_de_treize_cellules_est_refusee() {
        List<String> quatorze = IntStream.range(0, 14).mapToObj(String::valueOf).toList();
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> new Ligne("01", "", Genre.LIGNE, quatorze));
    }
}
