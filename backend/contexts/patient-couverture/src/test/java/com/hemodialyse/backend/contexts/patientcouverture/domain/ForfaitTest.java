package com.hemodialyse.backend.contexts.patientcouverture.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Règles du forfait du catalogue (RF2) : code et libellé obligatoires, prix facultatif (nul toléré, zéro admis, négatif refusé,
 * arrondi au centime), clé naturelle {@code code} dans le centre.
 */
class ForfaitTest {

    private static final CentreId CENTRE = new CentreId(UUID.fromString("99997700-0000-0000-0000-00000000000a"));
    private static final UUID ID = UUID.fromString("99997700-0000-0000-0000-0000000000f2");

    private static Forfait forfait(String code, String libelle, String prix) {
        return Forfait.referencer(ID, CENTRE, code, libelle, prix);
    }

    private static List<AnomalieDeSaisie> anomalies(String code, String libelle, String prix) {
        try {
            forfait(code, libelle, prix);
        } catch (SaisieDeForfaitInvalide refus) {
            return refus.anomalies();
        }
        throw new AssertionError("saisie acceptée : " + code + " / " + libelle + " / " + prix);
    }

    @Test
    void un_forfait_garde_son_code_son_libelle_et_son_prix_nettoyes_des_espaces_et_des_espaces_insecables() {
        Forfait f = forfait("  HD-CONV ", " Hémodialyse conventionnelle ", " 5600 ");

        assertThat(f.code()).isEqualTo("HD-CONV");
        assertThat(f.libelle()).isEqualTo("Hémodialyse conventionnelle");
        assertThat(f.prix()).isEqualByComparingTo("5600.00");
        assertThat(f.id()).isEqualTo(ID);
        assertThat(f.centre()).isEqualTo(CENTRE);
    }

    @Test
    void le_prix_est_facultatif_vide_ou_blanc_il_est_absent_et_zero_est_un_prix() {
        assertThat(forfait("F", "Forfait", null).prix()).isNull();
        assertThat(forfait("F", "Forfait", "").prix()).isNull();
        assertThat(forfait("F", "Forfait", "   ").prix()).isNull();
        assertThat(forfait("F", "Forfait", "0").prix()).isEqualByComparingTo("0");
        assertThat(forfait("F", "Forfait", "-0").prix()).isEqualByComparingTo("0");
    }

    @Test
    void le_prix_accepte_la_virgule_les_espaces_de_milliers_le_signe_plus_et_la_notation_scientifique() {
        assertThat(forfait("F", "Forfait", "5600,5").prix()).hasToString("5600.50");
        assertThat(forfait("F", "Forfait", "5 600,50").prix()).hasToString("5600.50");
        assertThat(forfait("F", "Forfait", "5 600").prix()).hasToString("5600.00");
        assertThat(forfait("F", "Forfait", "5 600").prix()).hasToString("5600.00");
        assertThat(forfait("F", "Forfait", "+12").prix()).hasToString("12.00");
        assertThat(forfait("F", "Forfait", "1e3").prix()).hasToString("1000.00");
    }

    @Test
    void le_prix_est_arrondi_au_centime_vers_le_haut_a_partir_de_la_demie() {
        assertThat(forfait("F", "Forfait", "10.005").prix()).hasToString("10.01");
        assertThat(forfait("F", "Forfait", "10.004").prix()).hasToString("10.00");
        assertThat(forfait("F", "Forfait", "0.004").prix()).hasToString("0.00");
        assertThat(forfait("F", "Forfait", "99999999.994").prix()).hasToString("99999999.99");
    }

    @Test
    void le_code_et_le_libelle_sont_obligatoires() {
        for (String vide : new String[]{null, "", "   ", " "}) {
            assertThat(anomalies(vide, "Forfait", null)).containsExactly(
                    new AnomalieDeSaisie("code", "REQUIRED", "« Code » est obligatoire.", Map.of()));
            assertThat(anomalies("F", vide, null)).containsExactly(
                    new AnomalieDeSaisie("libelle", "REQUIRED", "« Libellé » est obligatoire.", Map.of()));
        }
    }

    @Test
    void le_code_ne_depasse_pas_50_caracteres_et_le_libelle_255() {
        assertThat(forfait("C".repeat(50), "L".repeat(255), null).code()).hasSize(50);
        assertThat(anomalies("C".repeat(51), "Forfait", null)).containsExactly(new AnomalieDeSaisie("code", "TOO_LONG",
                "« Code » dépasse 50 caractères (51).", Map.of("max", "50", "length", "51")));
        assertThat(anomalies("F", "L".repeat(256), null)).containsExactly(new AnomalieDeSaisie("libelle", "TOO_LONG",
                "« Libellé » dépasse 255 caractères (256).", Map.of("max", "255", "length", "256")));
    }

    @Test
    void un_prix_illisible_negatif_ou_trop_grand_est_refuse_avec_la_valeur_saisie() {
        assertThat(anomalies("F", "Forfait", "abc")).containsExactly(new AnomalieDeSaisie("prix", "INVALID_NUMBER",
                "« Prix » : « abc » n'est pas un nombre (ex. 5600 ou 5600,50).", Map.of("value", "abc")));
        assertThat(anomalies("F", "Forfait", "5600 DA")).extracting(AnomalieDeSaisie::code).containsExactly("INVALID_NUMBER");
        assertThat(anomalies("F", "Forfait", "-1")).containsExactly(new AnomalieDeSaisie("prix", "NEGATIVE_NUMBER",
                "« Prix » ne peut pas être négatif.", Map.of("value", "-1")));
        assertThat(anomalies("F", "Forfait", "-0.001")).extracting(AnomalieDeSaisie::code).containsExactly("NEGATIVE_NUMBER");
        assertThat(anomalies("F", "Forfait", "100000000")).containsExactly(new AnomalieDeSaisie("prix", "NUMBER_TOO_LARGE",
                "« Prix » est trop grand (maximum 99999999.99).", Map.of("value", "100000000", "max", "99999999.99")));
        assertThat(anomalies("F", "Forfait", "99999999.995")).extracting(AnomalieDeSaisie::code).containsExactly("NUMBER_TOO_LARGE");
        assertThat(forfait("F", "Forfait", "99999999.99").prix()).hasToString("99999999.99");
    }

    @Test
    void un_prix_a_l_exposant_demesure_ou_trop_long_est_refuse_vite_comme_un_nombre_invalide() {
        for (String demesure : new String[]{"1e999999999", "1E-999999999", "0e999999999", "1".repeat(33), "0." + "0".repeat(40) + "1"}) {
            assertThat(org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(java.time.Duration.ofSeconds(2),
                    () -> anomalies("F", "Forfait", demesure))).extracting(AnomalieDeSaisie::code).containsExactly("INVALID_NUMBER");
        }
        assertThat(forfait("F", "Forfait", "1e5").prix()).hasToString("100000.00");
        assertThat(anomalies("F", "Forfait", "1e9")).extracting(AnomalieDeSaisie::code).containsExactly("NUMBER_TOO_LARGE");
    }

    @Test
    void toutes_les_anomalies_sont_rendues_ensemble_dans_l_ordre_code_libelle_prix() {
        assertThat(anomalies("", "", "-5")).extracting(AnomalieDeSaisie::champ, AnomalieDeSaisie::code).containsExactly(
                org.assertj.core.groups.Tuple.tuple("code", "REQUIRED"), org.assertj.core.groups.Tuple.tuple("libelle", "REQUIRED"),
                org.assertj.core.groups.Tuple.tuple("prix", "NEGATIVE_NUMBER"));
    }

    @Test
    void la_cle_naturelle_est_le_code_sans_casse_ni_espaces_repetes() {
        assertThat(forfait("  HD   Conv ", "Forfait", null).cle()).isEqualTo("hd conv");
        assertThat(forfait("hd conv", "Autre", "1").cle()).isEqualTo(forfait("HD  CONV", "Forfait", null).cle());
    }

    @Test
    void corriger_remplace_tous_les_champs_en_gardant_l_identite_et_le_centre_le_prix_omis_est_efface() {
        Forfait f = forfait("HD-CONV", "Forfait", "5600");

        Forfait corrige = f.corriger("HD-CONV2", "Forfait corrigé", null);

        assertThat(corrige.id()).isEqualTo(ID);
        assertThat(corrige.centre()).isEqualTo(CENTRE);
        assertThat(corrige.code()).isEqualTo("HD-CONV2");
        assertThat(corrige.prix()).isNull();
        assertThatThrownBy(() -> f.corriger("", "Forfait", null)).isInstanceOf(SaisieDeForfaitInvalide.class);
    }

    @Test
    void l_unicite_refuse_un_autre_forfait_de_meme_code_mais_pas_le_meme_forfait() {
        Forfait f = forfait("HD-CONV", "Forfait", null);
        Forfait homonyme = Forfait.referencer(UUID.randomUUID(), CENTRE, " hd-conv ", "Autre", null);

        f.verifierLUnicite(List.of(f));
        assertThatThrownBy(() -> f.verifierLUnicite(List.of(f, homonyme))).isInstanceOf(ForfaitDejaExistant.class);
        f.verifierLUnicite(List.of(Forfait.referencer(UUID.randomUUID(), CENTRE, "HD-AUTRE", "Autre", null)));
    }

    @Test
    void un_forfait_reconstitue_garde_tel_quel_son_prix_y_compris_negatif_ou_sans_echelle() {
        Forfait historique = Forfait.reconstituer(ID, CENTRE, "HIST", "Historique", new BigDecimal("-5.00"));

        assertThat(historique.prix()).hasToString("-5.00");
        assertThat(Forfait.reconstituer(ID, CENTRE, "HIST", "Historique", null).prix()).isNull();
    }
}
