package com.hemodialyse.backend.contexts.patientcouverture.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PL3 (KB-PL3-4, décision du 2026-11-11) : le délai d'échéance du tableau de bord est de 30 jours par défaut, zéro admis, négatif
 * refusé ; le mois du tableau de bord est lu strictement (AAAA-MM), un mois illisible est refusé au lieu d'être ignoré.
 */
class DelaiDEcheanceTest {

    private static final LocalDate JOUR = LocalDate.of(2026, 11, 11);

    @Test
    void un_delai_absent_vaut_trente_jours() {
        assertThat(DelaiDEcheance.de(null).jours()).isEqualTo(30);
        assertThat(DelaiDEcheance.PAR_DEFAUT.jours()).isEqualTo(30);
    }

    @Test
    void le_seuil_est_le_jour_du_centre_plus_le_delai_et_zero_est_admis() {
        assertThat(DelaiDEcheance.de(15).seuilAu(JOUR)).isEqualTo(LocalDate.of(2026, 11, 26));
        assertThat(DelaiDEcheance.de(0).seuilAu(JOUR)).isEqualTo(JOUR);
    }

    @Test
    void un_delai_negatif_est_refuse() {
        assertThatThrownBy(() -> DelaiDEcheance.de(-1)).isInstanceOf(SaisieInvalide.class)
                .hasMessageContaining("expirationDays");
    }

    @Test
    void un_mois_absent_ou_vide_n_est_pas_un_filtre() {
        assertThat(MoisDuTableauDeBord.lire(null)).isEmpty();
        assertThat(MoisDuTableauDeBord.lire("")).isEmpty();
        assertThat(MoisDuTableauDeBord.lire("   ")).isEmpty();
    }

    @Test
    void un_mois_au_format_aaaa_mm_est_lu() {
        assertThat(MoisDuTableauDeBord.lire("2026-01")).isEqualTo(Optional.of(YearMonth.of(2026, 1)));
    }

    @Test
    void un_mois_illisible_est_refuse() {
        for (String brut : new String[]{"2026-1", "n-importe-quoi", "2026-13", "2026/01"}) {
            assertThatThrownBy(() -> MoisDuTableauDeBord.lire(brut)).as(brut).isInstanceOf(SaisieInvalide.class)
                    .hasMessageContaining("month");
        }
    }
}
