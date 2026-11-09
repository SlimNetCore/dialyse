package com.hemodialyse.backend.contexts.pilotagedirection.domain;

import com.hemodialyse.backend.contexts.pilotagedirection.domain.MatriceDesCaisses.CelluleDeCaisse;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.MatriceDesCaisses.CentreDeLaMatrice;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.MatriceDesCaisses.TotalDeCaisse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MatriceDesCaissesTest {

    private static final List<CentreDeLaMatrice> CENTRES = List.of(new CentreDeLaMatrice("a", "Annaba"), new CentreDeLaMatrice("b", "Blida"));

    @Test
    void une_caisse_sans_ligne_pour_un_centre_vaut_zero_dans_ce_centre() {
        var cellules = List.of(new CelluleDeCaisse("a", "CNAS", 8L, 20L, new BigDecimal("100.00")));
        var totaux = List.of(new TotalDeCaisse("CNAS", "CNAS", 8L, 20L, new BigDecimal("100.00")));

        var ligne = MatriceDesCaisses.constituer(CENTRES, cellules, totaux).getFirst();

        assertThat(ligne.patients()).containsExactly(8L, 0L);
        assertThat(ligne.seances()).containsExactly(20L, 0L);
        assertThat(ligne.caHt()).hasSize(2);
        assertThat(ligne.caHt().get(1)).isEqualByComparingTo("0");
    }

    @Test
    void un_effectif_masque_reste_masque_et_ne_devient_jamais_zero() {
        var cellules = List.of(new CelluleDeCaisse("a", "CNAS", null, 20L, new BigDecimal("100.00")));
        var totaux = List.of(new TotalDeCaisse("CNAS", "CNAS", null, 20L, new BigDecimal("100.00")));

        var ligne = MatriceDesCaisses.constituer(CENTRES, cellules, totaux).getFirst();

        assertThat(ligne.patients()).containsExactly(null, 0L);
        assertThat(ligne.totalPatients()).isNull();
        assertThat(ligne.totalSeances()).isEqualTo(20L);
    }

    @Test
    void une_caisse_sans_nom_sappelle_caisse_non_renseignee() {
        var totaux = Arrays.asList(new TotalDeCaisse("", null, 6L, 1L, BigDecimal.ONE), new TotalDeCaisse("X", "", 6L, 1L, BigDecimal.ONE));

        var lignes = MatriceDesCaisses.constituer(CENTRES, List.of(), totaux);

        assertThat(lignes).extracting(l -> l.caisse()).containsExactly("Caisse non renseignée", "Caisse non renseignée");
    }

    @Test
    void les_lignes_suivent_lordre_des_totaux_et_les_colonnes_celui_des_centres() {
        var totaux = List.of(new TotalDeCaisse("B", "Seconde", 1L, 1L, BigDecimal.ONE), new TotalDeCaisse("A", "Première", 1L, 1L, BigDecimal.ONE));

        var lignes = MatriceDesCaisses.constituer(CENTRES, List.of(), totaux);

        assertThat(lignes).extracting(l -> l.caisse()).containsExactly("Seconde", "Première");
        assertThat(lignes.getFirst().patients()).hasSameSizeAs(CENTRES);
    }

    @Test
    void une_cellule_dun_autre_centre_ou_dune_autre_caisse_nest_pas_comptee() {
        var cellules = List.of(new CelluleDeCaisse("a", "AUTRE", 9L, 9L, BigDecimal.TEN), new CelluleDeCaisse("z", "CNAS", 9L, 9L, BigDecimal.TEN));
        var totaux = List.of(new TotalDeCaisse("CNAS", "CNAS", 0L, 0L, BigDecimal.ZERO));

        var ligne = MatriceDesCaisses.constituer(CENTRES, cellules, totaux).getFirst();

        assertThat(ligne.patients()).containsExactly(0L, 0L);
    }
}
