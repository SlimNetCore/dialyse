package com.hemodialyse.backend.contexts.comptabilite.domain.tva;

import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RegleTvaTest {

    private static final CentreId CENTRE = new CentreId(UUID.randomUUID());
    private static final CentreId AUTRE_CENTRE = new CentreId(UUID.randomUUID());
    private static final String TYPE = "HEMODIALYSE";

    private static LocalDate jour(String date) {
        return LocalDate.parse(date);
    }

    private static RegleTva regle(String type, String debut, String fin) {
        return RegleTva.definir(CENTRE, type, new BigDecimal("19.00"), false, jour(debut), fin == null ? null : jour(fin), null);
    }

    private static RegleTva existante(CentreId centre, String type, String debut, String fin, boolean active) {
        return RegleTva.reconstituee(centre, type, new BigDecimal("19.00"), false, jour(debut), fin == null ? null : jour(fin),
                null, active);
    }

    // ------------------------------------------------------------------ valeurs

    private static void accepte(RegleTva nouvelle, RegleTva... existantes) {
        assertThatCode(() -> nouvelle.exigerSansChevauchement(List.of(existantes))).doesNotThrowAnyException();
    }

    private static void refuse(RegleTva nouvelle, RegleTva... existantes) {
        assertThatThrownBy(() -> nouvelle.exigerSansChevauchement(List.of(existantes)))
                .isInstanceOf(RegleTvaEnChevauchement.class).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void une_regle_a_un_type_un_taux_et_un_debut_et_est_active() {
        RegleTva r = RegleTva.definir(CENTRE, TYPE, new BigDecimal("9"), true, jour("2026-01-01"), null, "Loi 2026");

        assertThat(r.typePrestation()).isEqualTo(TYPE);
        assertThat(r.taux().valeur()).isEqualByComparingTo("9");
        assertThat(r.exonere()).isTrue();
        assertThat(r.debutValidite()).isEqualTo(jour("2026-01-01"));
        assertThat(r.finValidite()).isNull();
        assertThat(r.texteReference()).isEqualTo("Loi 2026");
        assertThat(r.active()).isTrue();
        assertThat(r.centre()).isEqualTo(CENTRE);
    }

    @Test
    void le_type_de_prestation_est_obligatoire_mais_garde_ses_espaces_et_sa_casse() {
        for (String vide : new String[]{null, "", "   "}) {
            assertThatThrownBy(() -> regle(vide, "2026-01-01", null)).isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Le type de prestation est obligatoire");
        }
        assertThat(regle(" HEMODIALYSE ", "2026-01-01", null).typePrestation()).isEqualTo(" HEMODIALYSE ");
        assertThat(regle("hemodialyse", "2026-01-01", null).estDuType(TYPE)).isFalse();
        assertThat(regle(TYPE, "2026-01-01", null).estDuType(TYPE)).isTrue();
    }

    @Test
    void le_taux_est_obligatoire_et_positif_ou_nul_sans_borne_haute() {
        assertThatThrownBy(() -> RegleTva.definir(CENTRE, TYPE, null, false, jour("2026-01-01"), null, null))
                .hasMessage("Le taux TVA ne peut pas être négatif");
        assertThatThrownBy(() -> RegleTva.definir(CENTRE, TYPE, new BigDecimal("-0.01"), false, jour("2026-01-01"), null, null))
                .hasMessage("Le taux TVA ne peut pas être négatif");
        assertThat(RegleTva.definir(CENTRE, TYPE, BigDecimal.ZERO, false, jour("2026-01-01"), null, null).taux().valeur())
                .isEqualByComparingTo("0");
        assertThat(RegleTva.definir(CENTRE, TYPE, new BigDecimal("999.99"), false, jour("2026-01-01"), null, null).taux().valeur())
                .isEqualByComparingTo("999.99");
        assertThat(new TauxDeTva(new BigDecimal("19.50")).toString()).isEqualTo("19.50");
    }

    @Test
    void la_date_de_debut_est_obligatoire() {
        assertThatThrownBy(() -> RegleTva.definir(CENTRE, TYPE, BigDecimal.TEN, false, null, null, null))
                .hasMessage("La date de début de validité est obligatoire");
    }

    // ------------------------------------------------------------------ règle applicable

    @Test
    void la_fin_avant_le_debut_est_refusee_et_la_fin_egale_au_debut_est_acceptee() {
        assertThatThrownBy(() -> regle(TYPE, "2026-06-01", "2026-01-01")).isInstanceOf(DatesDeValiditeInversees.class)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("La date de fin de validité (2026-01-01) précède la date de début (2026-06-01) : la règle ne s'appliquerait jamais");
        assertThat(regle(TYPE, "2026-07-01", "2026-07-01").finValidite()).isEqualTo(jour("2026-07-01"));
        assertThatThrownBy(() -> regle(TYPE, "2026-06-01", "2026-05-31")).isInstanceOf(DatesDeValiditeInversees.class);
    }

    @Test
    void une_regle_reconstituee_depuis_la_base_n_est_pas_controlee_sur_ses_dates() {
        RegleTva r = existante(CENTRE, TYPE, "2026-06-01", "2026-01-01", true);

        assertThat(r.finValidite()).isEqualTo(jour("2026-01-01"));
        assertThat(r.estApplicableLe(jour("2026-03-01"))).isFalse();
    }

    @Test
    void une_regle_s_applique_de_son_debut_a_sa_fin_bornes_incluses() {
        RegleTva r = regle(TYPE, "2026-01-01", "2026-06-30");

        assertThat(r.estApplicableLe(jour("2025-12-31"))).isFalse();
        assertThat(r.estApplicableLe(jour("2026-01-01"))).isTrue();
        assertThat(r.estApplicableLe(jour("2026-06-30"))).isTrue();
        assertThat(r.estApplicableLe(jour("2026-07-01"))).isFalse();
    }

    // ------------------------------------------------------------------ chevauchement

    @Test
    void une_regle_sans_fin_est_ouverte() {
        RegleTva r = regle(TYPE, "2026-01-01", null);

        assertThat(r.estApplicableLe(jour("2099-12-31"))).isTrue();
        assertThat(r.estApplicableLe(jour("2025-12-31"))).isFalse();
    }

    @Test
    void une_regle_desactivee_ne_s_applique_jamais() {
        assertThat(existante(CENTRE, TYPE, "2026-01-01", null, false).estApplicableLe(jour("2026-02-01"))).isFalse();
    }

    @Test
    void une_regle_ne_chevauche_rien_quand_le_centre_n_a_aucune_regle() {
        accepte(regle(TYPE, "2026-01-01", null));
    }

    @Test
    void deux_regles_ouvertes_du_meme_type_se_chevauchent() {
        refuse(regle(TYPE, "2026-02-01", null), existante(CENTRE, TYPE, "2026-01-01", null, true));
        refuse(regle(TYPE, "2026-01-01", null), existante(CENTRE, TYPE, "2026-01-01", null, true));
    }

    @Test
    void une_regle_qui_commence_apres_la_fin_de_l_existante_ne_la_chevauche_pas() {
        accepte(regle(TYPE, "2026-07-01", null), existante(CENTRE, TYPE, "2026-01-01", "2026-06-30", true));
    }

    @Test
    void une_regle_qui_se_termine_avant_le_debut_de_l_existante_ne_la_chevauche_pas() {
        accepte(regle(TYPE, "2025-01-01", "2025-12-31"), existante(CENTRE, TYPE, "2026-01-01", null, true));
    }

    @Test
    void les_bornes_sont_incluses_un_jour_commun_suffit_pour_se_chevaucher() {
        refuse(regle(TYPE, "2026-06-30", null), existante(CENTRE, TYPE, "2026-01-01", "2026-06-30", true));
        refuse(regle(TYPE, "2025-01-01", "2026-01-01"), existante(CENTRE, TYPE, "2026-01-01", null, true));
        refuse(regle(TYPE, "2026-03-01", "2026-03-01"), existante(CENTRE, TYPE, "2026-01-01", "2026-12-31", true));
    }

    @Test
    void une_regle_incluse_dans_l_existante_ou_qui_l_englobe_la_chevauche() {
        refuse(regle(TYPE, "2026-03-01", "2026-04-30"), existante(CENTRE, TYPE, "2026-01-01", "2026-12-31", true));
        refuse(regle(TYPE, "2025-01-01", "2027-12-31"), existante(CENTRE, TYPE, "2026-01-01", "2026-12-31", true));
    }

    @Test
    void un_autre_type_un_autre_centre_ou_une_regle_desactivee_ne_comptent_pas() {
        accepte(regle(TYPE, "2026-01-01", null),
                existante(CENTRE, "MEDICAMENT", "2026-01-01", null, true),
                existante(AUTRE_CENTRE, TYPE, "2026-01-01", null, true),
                existante(CENTRE, TYPE, "2026-01-01", null, false));
    }

    @Test
    void le_type_se_compare_sans_normalisation() {
        accepte(regle(TYPE, "2026-01-01", null), existante(CENTRE, "hemodialyse", "2026-01-01", null, true),
                existante(CENTRE, " HEMODIALYSE ", "2026-01-01", null, true));
    }

    @Test
    void le_refus_cite_les_deux_periodes() {
        assertThatThrownBy(() -> regle(TYPE, "2026-06-01", "2027-06-30")
                .exigerSansChevauchement(List.of(existante(CENTRE, TYPE, "2026-01-01", null, true))))
                .hasMessage("La règle de TVA HEMODIALYSE du 2026-06-01 au 2027-06-30 chevauche la règle existante à partir du "
                        + "2026-01-01 : les dates de deux règles du même type ne doivent pas se recouvrir");
    }
}
