package com.hemodialyse.backend.contexts.dossiermedical.domain;

import com.hemodialyse.backend.contexts.dossiermedical.domain.RisqueInfectieux.SerologieObservee;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** La règle Java du risque infectieux (RG-PLN-012 vue du Dossier médical) : mêmes cas que les goldens DM9 {@code planification/*}. */
class RisqueInfectieuxTest {

    private static final UUID P = new UUID(1, 1);
    private static final LocalDate J = LocalDate.of(2026, 1, 10);
    private static final Instant S10 = Instant.parse("2026-01-01T10:00:00Z");
    private static final Instant S11 = Instant.parse("2026-01-01T11:00:00Z");

    private static SerologieObservee s(MarqueurSerologique m, ResultatSerologique r, LocalDate j, Instant saisie) {
        return new SerologieObservee(P, m, r, j, saisie);
    }

    @Test
    void seul_un_positif_d_un_marqueur_surveille_met_a_risque() {
        assertThat(RisqueInfectieux.patientsARisque(List.of())).isEmpty();
        assertThat(RisqueInfectieux.patientsARisque(List.of(s(MarqueurSerologique.AG_HBS, ResultatSerologique.POSITIF, J, S10)))).containsExactly(P);
        assertThat(RisqueInfectieux.patientsARisque(List.of(s(MarqueurSerologique.TPHA, ResultatSerologique.POSITIF, J, S10)))).isEmpty();
        assertThat(RisqueInfectieux.patientsARisque(List.of(s(MarqueurSerologique.VIH_AC, ResultatSerologique.DOUTEUX, J, S10)))).isEmpty();
        assertThat(RisqueInfectieux.patientsARisque(List.of(s(MarqueurSerologique.ARN_VHC, ResultatSerologique.EN_COURS, J, S10)))).isEmpty();
    }

    @Test
    void la_derniere_serologie_du_marqueur_tranche_departagee_par_le_prelevement_puis_la_saisie() {
        assertThat(RisqueInfectieux.patientsARisque(List.of(
                s(MarqueurSerologique.AG_HBS, ResultatSerologique.POSITIF, J, S10),
                s(MarqueurSerologique.AG_HBS, ResultatSerologique.NEGATIF, J.plusDays(5), S10)))).isEmpty();
        assertThat(RisqueInfectieux.patientsARisque(List.of(
                s(MarqueurSerologique.AG_HBS, ResultatSerologique.NEGATIF, J, S10),
                s(MarqueurSerologique.AG_HBS, ResultatSerologique.POSITIF, J.plusDays(5), S10)))).containsExactly(P);
        assertThat(RisqueInfectieux.patientsARisque(List.of(
                s(MarqueurSerologique.AG_HBS, ResultatSerologique.POSITIF, J, S10),
                s(MarqueurSerologique.AG_HBS, ResultatSerologique.NEGATIF, J, S11)))).isEmpty();
        assertThat(RisqueInfectieux.patientsARisque(List.of(
                s(MarqueurSerologique.AG_HBS, ResultatSerologique.NEGATIF, J, S10),
                s(MarqueurSerologique.AG_HBS, ResultatSerologique.POSITIF, J, S11)))).containsExactly(P);
    }

    /** KB-MED-106 (CORRIGÉ 2026-11-09, ADR 0113) : seul un POSITIF ou un NEGATIF est conclusif ; un douteux ou un en cours plus récent n'efface pas un positif. */
    @Test
    void un_douteux_ou_un_en_cours_plus_recent_ne_leve_pas_le_risque_kb_med_106() {
        assertThat(RisqueInfectieux.patientsARisque(List.of(
                s(MarqueurSerologique.AG_HBS, ResultatSerologique.POSITIF, J, S10),
                s(MarqueurSerologique.AG_HBS, ResultatSerologique.DOUTEUX, J.plusDays(5), S10)))).containsExactly(P);
        assertThat(RisqueInfectieux.patientsARisque(List.of(
                s(MarqueurSerologique.AG_HBS, ResultatSerologique.POSITIF, J, S10),
                s(MarqueurSerologique.AG_HBS, ResultatSerologique.EN_COURS, J.plusDays(5), S10)))).containsExactly(P);
    }

    @Test
    void seul_le_dernier_resultat_conclusif_decide_kb_med_106() {
        // positif puis négatif : sorti du risque
        assertThat(RisqueInfectieux.patientsARisque(List.of(
                s(MarqueurSerologique.AG_HBS, ResultatSerologique.POSITIF, J, S10),
                s(MarqueurSerologique.AG_HBS, ResultatSerologique.NEGATIF, J.plusDays(5), S10)))).isEmpty();
        // douteux seul : pas de risque
        assertThat(RisqueInfectieux.patientsARisque(List.of(
                s(MarqueurSerologique.AG_HBS, ResultatSerologique.DOUTEUX, J, S10)))).isEmpty();
        // négatif puis douteux : pas de risque
        assertThat(RisqueInfectieux.patientsARisque(List.of(
                s(MarqueurSerologique.AG_HBS, ResultatSerologique.NEGATIF, J, S10),
                s(MarqueurSerologique.AG_HBS, ResultatSerologique.DOUTEUX, J.plusDays(5), S10)))).isEmpty();
        // positif, négatif, puis douteux : pas de risque
        assertThat(RisqueInfectieux.patientsARisque(List.of(
                s(MarqueurSerologique.AG_HBS, ResultatSerologique.POSITIF, J, S10),
                s(MarqueurSerologique.AG_HBS, ResultatSerologique.NEGATIF, J.plusDays(5), S10),
                s(MarqueurSerologique.AG_HBS, ResultatSerologique.DOUTEUX, J.plusDays(9), S10)))).isEmpty();
        // négatif, positif, puis en cours : à risque
        assertThat(RisqueInfectieux.patientsARisque(List.of(
                s(MarqueurSerologique.AG_HBS, ResultatSerologique.NEGATIF, J, S10),
                s(MarqueurSerologique.AG_HBS, ResultatSerologique.POSITIF, J.plusDays(5), S10),
                s(MarqueurSerologique.AG_HBS, ResultatSerologique.EN_COURS, J.plusDays(9), S10)))).containsExactly(P);
        // positif puis douteux le même jour (saisie plus tardive) : à risque
        assertThat(RisqueInfectieux.patientsARisque(List.of(
                s(MarqueurSerologique.AG_HBS, ResultatSerologique.POSITIF, J, S10),
                s(MarqueurSerologique.AG_HBS, ResultatSerologique.DOUTEUX, J, S11)))).containsExactly(P);
    }

    @Test
    void les_marqueurs_sont_independants_pour_le_dernier_resultat_conclusif_kb_med_106() {
        assertThat(RisqueInfectieux.patientsARisque(List.of(
                s(MarqueurSerologique.AG_HBS, ResultatSerologique.POSITIF, J, S10),
                s(MarqueurSerologique.AG_HBS, ResultatSerologique.NEGATIF, J.plusDays(5), S10),
                s(MarqueurSerologique.VIH_AC, ResultatSerologique.POSITIF, J, S10),
                s(MarqueurSerologique.VIH_AC, ResultatSerologique.DOUTEUX, J.plusDays(5), S10)))).containsExactly(P);
    }

    /** KB-MED-107 (reproduit) : à égalité totale, rien n'est « plus récent » que le positif : il l'emporte. */
    @Test
    void a_egalite_totale_le_positif_l_emporte_kb_med_107() {
        assertThat(RisqueInfectieux.patientsARisque(List.of(
                s(MarqueurSerologique.AG_HBS, ResultatSerologique.POSITIF, J, S10),
                s(MarqueurSerologique.AG_HBS, ResultatSerologique.NEGATIF, J, S10)))).containsExactly(P);
    }

    @Test
    void un_autre_marqueur_plus_recent_ne_change_rien_et_un_seul_marqueur_positif_suffit() {
        assertThat(RisqueInfectieux.patientsARisque(List.of(
                s(MarqueurSerologique.AG_HBS, ResultatSerologique.NEGATIF, J.plusDays(9), S10),
                s(MarqueurSerologique.VIH_AC, ResultatSerologique.POSITIF, J, S10),
                s(MarqueurSerologique.AC_VHC, ResultatSerologique.NEGATIF, J.plusDays(9), S10)))).containsExactly(P);
    }
}
