package com.hemodialyse.backend.contexts.patientcouverture.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Règles du transporteur (RF1) : nom obligatoire de 255 caractères au plus, téléphone facultatif, clé naturelle {@code nom}. */
class TransporteurTest {

    private static final CentreId CENTRE = new CentreId(UUID.fromString("99997700-0000-0000-0000-00000000000a"));
    private static final UUID ID = UUID.fromString("99997700-0000-0000-0000-0000000000f1");

    private static Transporteur transporteur(String nom, String telephone) {
        return Transporteur.referencer(ID, CENTRE, nom, telephone);
    }

    private static List<AnomalieDeSaisie> anomalies(String nom, String telephone) {
        try {
            transporteur(nom, telephone);
        } catch (SaisieDeTransporteurInvalide refus) {
            return refus.anomalies();
        }
        throw new AssertionError("saisie acceptée : " + nom + " / " + telephone);
    }

    @Test
    void un_transporteur_garde_son_nom_et_son_telephone_nettoyes_des_espaces_et_des_espaces_insecables() {
        Transporteur t = transporteur("   Ambulances Rouiba  ", " 0795 00 61 36 ");

        assertThat(t.nom()).isEqualTo("Ambulances Rouiba");
        assertThat(t.telephone()).isEqualTo("0795 00 61 36");
        assertThat(t.id()).isEqualTo(ID);
        assertThat(t.centre()).isEqualTo(CENTRE);
    }

    @Test
    void le_telephone_est_facultatif_vide_ou_blanc_il_est_absent() {
        assertThat(transporteur("Taxi", null).telephone()).isNull();
        assertThat(transporteur("Taxi", "").telephone()).isNull();
        assertThat(transporteur("Taxi", "   ").telephone()).isNull();
    }

    @Test
    void le_nom_est_obligatoire() {
        for (String nom : new String[]{null, "", "   ", " "}) {
            assertThat(anomalies(nom, null)).containsExactly(
                    new AnomalieDeSaisie("nom", "REQUIRED", "« Nom » est obligatoire.", Map.of()));
        }
    }

    @Test
    void le_nom_ne_depasse_pas_255_caracteres() {
        assertThat(transporteur("N".repeat(255), null).nom()).hasSize(255);

        assertThat(anomalies("N".repeat(256), null)).containsExactly(new AnomalieDeSaisie("nom", "TOO_LONG",
                "« Nom » dépasse 255 caractères (256).", Map.of("max", "255", "length", "256")));
    }

    @Test
    void le_telephone_n_accepte_que_chiffres_signes_et_separateurs_et_six_caracteres_au_moins() {
        for (String valide : new String[]{"123456", "0795 00 61 36", "+213 (0)21-12.34/56"}) {
            assertThat(transporteur("Taxi", valide).telephone()).isEqualTo(valide);
        }
        for (String invalide : new String[]{"abcdef", "12345", "0795 00 61 3x"}) {
            assertThat(anomalies("Taxi", invalide)).containsExactly(new AnomalieDeSaisie("telephone", "INVALID_PHONE",
                    "« Téléphone » : « " + invalide + " » n'est pas un numéro de téléphone valide.", Map.of("value", invalide)));
        }
    }

    @Test
    void le_telephone_ne_depasse_pas_50_caracteres() {
        assertThat(transporteur("Taxi", "1".repeat(50)).telephone()).hasSize(50);

        assertThat(anomalies("Taxi", "1".repeat(51))).containsExactly(new AnomalieDeSaisie("telephone", "TOO_LONG",
                "« Téléphone » dépasse 50 caractères (51).", Map.of("max", "50", "length", "51")));
    }

    @Test
    void toutes_les_anomalies_sont_rendues_ensemble_le_nom_d_abord() {
        assertThat(anomalies("", "x")).extracting(AnomalieDeSaisie::champ, AnomalieDeSaisie::code)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("nom", "REQUIRED"),
                        org.assertj.core.groups.Tuple.tuple("telephone", "INVALID_PHONE"));
    }

    @Test
    void la_cle_naturelle_ignore_la_casse_et_les_espaces_repetes() {
        assertThat(transporteur("  AMBULANCES   rouiba ", null).cle()).isEqualTo(transporteur("Ambulances Rouiba", null).cle());
        assertThat(transporteur("Ambulances Rouiba", null).cle()).isNotEqualTo(transporteur("Ambulances Rouiba Est", null).cle());
    }

    @Test
    void corriger_remplace_le_nom_et_le_telephone_en_gardant_l_identite_et_le_centre_et_valide_de_nouveau() {
        Transporteur t = transporteur("Taxi", "0795006136");

        Transporteur corrige = t.corriger("Taxi Medical", null);

        assertThat(corrige.id()).isEqualTo(ID);
        assertThat(corrige.centre()).isEqualTo(CENTRE);
        assertThat(corrige.nom()).isEqualTo("Taxi Medical");
        assertThat(corrige.telephone()).isNull();
        assertThat(t.nom()).as("immuable").isEqualTo("Taxi");
        assertThatThrownBy(() -> t.corriger("", null)).isInstanceOf(SaisieDeTransporteurInvalide.class);
    }

    @Test
    void un_nom_deja_porte_dans_le_centre_est_refuse_sauf_par_le_transporteur_lui_meme() {
        Transporteur premier = transporteur("Taxi", null);
        Transporteur autre = Transporteur.referencer(UUID.randomUUID(), CENTRE, "  TAXI ", null);

        assertThatThrownBy(() -> autre.verifierLUnicite(List.of(premier))).isInstanceOf(TransporteurDejaExistant.class);
        premier.verifierLUnicite(List.of(premier));
        autre.verifierLUnicite(List.of());
    }
}
