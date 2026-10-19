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

    private static RegleTvaId id() {
        return new RegleTvaId(UUID.randomUUID());
    }

    private static RegleTva definir(String taux) {
        return RegleTva.definir(id(), CENTRE, TYPE, taux == null ? null : new BigDecimal(taux), false, jour("2026-01-01"), null,
                null);
    }

    private static RegleTva regle(String type, String debut, String fin) {
        return RegleTva.definir(id(), CENTRE, type, new BigDecimal("19.00"), false, jour(debut), fin == null ? null : jour(fin),
                null);
    }

    private static RegleTva existante(CentreId centre, String type, String debut, String fin, boolean active) {
        return RegleTva.reconstituee(id(), centre, null, type, new BigDecimal("19.00"), false, jour(debut),
                fin == null ? null : jour(fin), null, active);
    }

    private static void accepte(RegleTva nouvelle, RegleTva... existantes) {
        assertThatCode(() -> nouvelle.exigerSansChevauchement(List.of(existantes))).doesNotThrowAnyException();
    }

    private static void refuse(RegleTva nouvelle, RegleTva... existantes) {
        assertThatThrownBy(() -> nouvelle.exigerSansChevauchement(List.of(existantes)))
                .isInstanceOf(RegleTvaEnChevauchement.class).isInstanceOf(IllegalArgumentException.class);
    }

    // ------------------------------------------------------------------ valeurs

    @Test
    void une_regle_a_un_type_un_taux_et_un_debut_et_est_active() {
        RegleTvaId id = id();
        RegleTva r = RegleTva.definir(id, CENTRE, TYPE, new BigDecimal("9"), true, jour("2026-01-01"), null, "Loi 2026");

        assertThat(r.id()).isEqualTo(id);
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
    void le_libelle_est_celui_saisi_sinon_le_type_et_le_taux() {
        assertThat(definir("19.00").libelle()).isEqualTo("HEMODIALYSE 19.00%");
        assertThat(RegleTva.definir(id(), CENTRE, "TVA hémodialyse", TYPE, BigDecimal.TEN, false, jour("2026-01-01"), null, null)
                .libelle()).isEqualTo("TVA hémodialyse");
        for (String vide : new String[]{"", "  "}) {
            assertThatThrownBy(() -> RegleTva.definir(id(), CENTRE, vide, TYPE, BigDecimal.TEN, false, jour("2026-01-01"), null,
                    null)).isInstanceOf(IllegalArgumentException.class).hasMessage("Le libellé est obligatoire");
        }
    }

    @Test
    void l_identifiant_est_obligatoire() {
        assertThatThrownBy(() -> RegleTva.definir(null, CENTRE, TYPE, BigDecimal.TEN, false, jour("2026-01-01"), null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RegleTvaId(null)).isInstanceOf(IllegalArgumentException.class);
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
    void le_taux_saisi_va_de_zero_a_cent_pour_cent() {
        assertThatThrownBy(() -> definir(null)).hasMessage("Le taux TVA ne peut pas être négatif");
        assertThatThrownBy(() -> definir("-0.01")).hasMessage("Le taux TVA ne peut pas être négatif");
        assertThatThrownBy(() -> definir("100.01")).hasMessage("Le taux TVA ne peut pas dépasser 100%");
        assertThat(definir("0").taux().valeur()).isEqualByComparingTo("0");
        assertThat(definir("100").taux().valeur()).isEqualByComparingTo("100");
        assertThat(new TauxDeTva(new BigDecimal("19.50")).toString()).isEqualTo("19.50");
    }

    @Test
    void un_taux_relu_en_base_n_est_pas_borne_a_cent() {
        assertThat(RegleTva.reconstituee(id(), CENTRE, null, TYPE, new BigDecimal("150.00"), false, jour("2026-01-01"), null,
                null, true).taux().valeur()).isEqualByComparingTo("150");
    }

    @Test
    void la_date_de_debut_est_obligatoire() {
        assertThatThrownBy(() -> RegleTva.definir(id(), CENTRE, TYPE, BigDecimal.TEN, false, null, null, null))
                .hasMessage("La date de début de validité est obligatoire");
    }

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

    // ------------------------------------------------------------------ règle applicable

    @Test
    void une_regle_s_applique_de_son_debut_a_sa_fin_bornes_incluses() {
        RegleTva r = regle(TYPE, "2026-01-01", "2026-06-30");

        assertThat(r.estApplicableLe(jour("2025-12-31"))).isFalse();
        assertThat(r.estApplicableLe(jour("2026-01-01"))).isTrue();
        assertThat(r.estApplicableLe(jour("2026-06-30"))).isTrue();
        assertThat(r.estApplicableLe(jour("2026-07-01"))).isFalse();
    }

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

    // ------------------------------------------------------------------ chevauchement

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

    // ------------------------------------------------------------------ modification et désactivation (F1)

    @Test
    void la_modification_garde_l_identite_le_centre_et_l_etat() {
        RegleTva desactivee = existante(CENTRE, TYPE, "2023-01-01", "2023-12-31", false);

        RegleTva modifiee = desactivee.modifier("Modifié", "MEDICAMENT", new BigDecimal("9"), true, jour("2024-01-01"), null,
                "Décret");

        assertThat(modifiee.id()).isEqualTo(desactivee.id());
        assertThat(modifiee.centre()).isEqualTo(CENTRE);
        assertThat(modifiee.active()).isFalse();
        assertThat(modifiee.libelle()).isEqualTo("Modifié");
        assertThat(modifiee.typePrestation()).isEqualTo("MEDICAMENT");
        assertThat(modifiee.taux().valeur()).isEqualByComparingTo("9");
        assertThat(modifiee.exonere()).isTrue();
        assertThat(modifiee.debutValidite()).isEqualTo(jour("2024-01-01"));
        assertThat(modifiee.finValidite()).isNull();
        assertThat(modifiee.texteReference()).isEqualTo("Décret");
    }

    @Test
    void la_modification_suit_la_regle_unique_de_saisie() {
        RegleTva r = existante(CENTRE, TYPE, "2026-01-01", null, true);

        assertThatThrownBy(() -> r.modifier("T", TYPE, new BigDecimal("100.01"), false, jour("2026-01-01"), null, null))
                .hasMessage("Le taux TVA ne peut pas dépasser 100%");
        assertThatThrownBy(() -> r.modifier("T", TYPE, BigDecimal.TEN, false, jour("2026-02-01"), jour("2026-01-31"), null))
                .isInstanceOf(DatesDeValiditeInversees.class);
        assertThat(r.modifier("T", TYPE, BigDecimal.TEN, false, jour("2026-02-01"), jour("2026-02-01"), null).finValidite())
                .isEqualTo(jour("2026-02-01"));
    }

    @Test
    void une_regle_modifiee_ne_se_chevauche_pas_elle_meme_mais_chevauche_les_autres() {
        RegleTva r = existante(CENTRE, TYPE, "2026-01-01", null, true);
        RegleTva voisine = existante(CENTRE, TYPE, "2024-01-01", "2024-12-31", true);
        RegleTva modifiee = r.modifier(null, TYPE, BigDecimal.TEN, false, jour("2025-01-01"), null, null);

        accepte(modifiee, r);
        refuse(r.modifier(null, TYPE, BigDecimal.TEN, false, jour("2024-06-01"), null, null), r, voisine);
    }

    @Test
    void une_regle_desactivee_n_est_bloquee_par_rien() {
        RegleTva desactivee = existante(CENTRE, TYPE, "2026-01-01", null, false);

        accepte(desactivee.modifier(null, TYPE, BigDecimal.TEN, false, jour("2026-01-01"), null, null),
                existante(CENTRE, TYPE, "2026-01-01", null, true));
    }

    @Test
    void la_desactivation_garde_tout_le_reste() {
        RegleTva r = RegleTva.definir(id(), CENTRE, "Libellé", TYPE, BigDecimal.TEN, true, jour("2026-01-01"),
                jour("2026-12-31"), "Loi");

        RegleTva desactivee = r.desactiver();

        assertThat(desactivee.active()).isFalse();
        assertThat(desactivee.desactiver()).isEqualTo(desactivee);
        assertThat(desactivee).usingRecursiveComparison().ignoringFields("active").isEqualTo(r);
    }
}
