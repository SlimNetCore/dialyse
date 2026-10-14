package com.hemodialyse.backend.contexts.gmao.domain.intervenant;

import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.UtilisateurId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Matrice invariant -> test de l'agrégat Intervenant (R24, KB-GMAO-22 à 31). */
class IntervenantTest {

    private static final Instant T0 = Instant.parse("2026-03-01T09:00:00Z");
    private static final Instant T1 = Instant.parse("2026-03-15T10:30:00Z");
    private static final UtilisateurId ADMIN = new UtilisateurId(UUID.fromString("00000000-0000-0000-0000-0000000000a1"));
    private static final CentreId CENTRE = new CentreId(UUID.fromString("00000000-0000-0000-0000-0000000000c1"));
    private static final IntervenantId ID = new IntervenantId(UUID.fromString("00000000-0000-0000-0000-000000000001"));

    private static TarifIntervenant tarif(String valeur) {
        return new TarifIntervenant(new BigDecimal(valeur));
    }

    private static Intervenant interne() {
        return Intervenant.enregistrer(ID, CENTRE, "Bernard", NatureIntervenant.INTERNE, "0555", "b@c.test", tarif("35.00"), ADMIN, T0);
    }

    // ------------------------------------------------------------------ enregistrer (R24)

    @Test
    void should_enregistrer_intervenant_actif_with_given_coordonnees_and_tarif() {
        Intervenant intervenant = interne();

        assertThat(intervenant.id()).isEqualTo(ID);
        assertThat(intervenant.centreId()).isEqualTo(CENTRE);
        assertThat(intervenant.nom()).isEqualTo("Bernard");
        assertThat(intervenant.nature()).isEqualTo(NatureIntervenant.INTERNE);
        assertThat(intervenant.telephone()).isEqualTo("0555");
        assertThat(intervenant.email()).isEqualTo("b@c.test");
        assertThat(intervenant.tarif()).isEqualTo(tarif("35.00"));
        assertThat(intervenant.estActif()).isTrue();
    }

    @Test
    void should_emit_IntervenantEnregistre_when_registered() {
        assertThat(interne().tirerEvenements()).containsExactly(new IntervenantEnregistre(ID, CENTRE, NatureIntervenant.INTERNE, ADMIN, T0));
    }

    @Test
    void should_enregistrer_without_coordonnees_nor_tarif() {
        Intervenant externe = Intervenant.enregistrer(ID, CENTRE, "Acme", NatureIntervenant.EXTERNE, null, null, null, ADMIN, T0);

        assertThat(externe.tarif()).isNull();
        assertThat(externe.telephone()).isNull();
        assertThat(externe.email()).isNull();
    }

    @Test
    void should_not_trim_nor_deduplicate_the_nom() {
        Intervenant intervenant = Intervenant.enregistrer(ID, CENTRE, "  Bernard  ", NatureIntervenant.INTERNE, null, null, null, ADMIN, T0);

        assertThat(intervenant.nom()).isEqualTo("  Bernard  ");
    }

    @Test
    void should_reject_intervenant_without_nom() {
        for (String nom : new String[]{null, "", "   "}) {
            assertThatThrownBy(() -> Intervenant.enregistrer(ID, CENTRE, nom, NatureIntervenant.INTERNE, null, null, null, ADMIN, T0))
                    .isInstanceOf(NomRequis.class).hasMessage("Nom requis");
        }
    }

    @Test
    void should_reject_intervenant_without_nature() {
        assertThatThrownBy(() -> Intervenant.enregistrer(ID, CENTRE, "X", null, null, null, null, ADMIN, T0))
                .isInstanceOf(NatureRequise.class).hasMessage("Type d'intervenant requis");
    }

    @Test
    void should_reject_intervenant_without_centre() {
        assertThatThrownBy(() -> Intervenant.enregistrer(ID, null, "X", NatureIntervenant.INTERNE, null, null, null, ADMIN, T0))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Centre requis");
    }

    @Test
    void should_check_nom_before_nature() {
        assertThatThrownBy(() -> Intervenant.enregistrer(ID, CENTRE, "", null, null, null, null, ADMIN, T0))
                .isInstanceOf(NomRequis.class);
    }

    // ------------------------------------------------------------------ tarif horaire (R24)

    @Test
    void should_reject_negative_tarif() {
        assertThatThrownBy(() -> new TarifIntervenant(new BigDecimal("-0.01")))
                .isInstanceOf(TarifNegatif.class).hasMessage("Le tarif horaire ne peut pas être négatif");
    }

    @Test
    void should_accept_zero_tarif_including_negative_zero() {
        assertThat(new TarifIntervenant(BigDecimal.ZERO).valeur()).isEqualByComparingTo("0");
        assertThat(new TarifIntervenant(new BigDecimal("-0")).valeur()).isEqualByComparingTo("0");
        assertThat(new TarifIntervenant(new BigDecimal("0.00")).valeur()).isEqualByComparingTo("0");
    }

    @Test
    void should_keep_the_scale_received_for_the_tarif() {
        assertThat(new TarifIntervenant(new BigDecimal("12.345")).valeur()).hasToString("12.345");
        assertThat(new TarifIntervenant(new BigDecimal("1E+1")).valeur()).hasToString("1E+1");
        assertThat(new TarifIntervenant(new BigDecimal("45.5")).valeur().scale()).isEqualTo(1);
    }

    @Test
    void should_consider_same_value_whatever_the_scale() {
        assertThat(tarif("45.5").memeValeurQue(tarif("45.50"))).isTrue();
        assertThat(tarif("45.5").memeValeurQue(tarif("45.51"))).isFalse();
        assertThat(tarif("45.5").memeValeurQue(null)).isFalse();
    }

    @Test
    void should_have_no_tarif_when_value_is_absent() {
        assertThat(TarifIntervenant.ouAucun(null)).isNull();
        assertThat(TarifIntervenant.ouAucun(new BigDecimal("3"))).isEqualTo(tarif("3"));
    }

    @Test
    void should_require_a_value_to_build_a_tarif() {
        assertThatThrownBy(() -> new TarifIntervenant(null)).isInstanceOf(IllegalArgumentException.class);
    }

    // ------------------------------------------------------------------ nature

    @Test
    void should_recognize_exact_nature_codes_only() {
        assertThat(NatureIntervenant.depuis("INTERNE")).isEqualTo(NatureIntervenant.INTERNE);
        assertThat(NatureIntervenant.depuis("EXTERNE")).isEqualTo(NatureIntervenant.EXTERNE);
        for (String code : new String[]{"interne", "SOUS_TRAITANT", "", null}) {
            assertThatThrownBy(() -> NatureIntervenant.depuis(code)).isInstanceOf(NatureInconnue.class);
        }
    }

    @Test
    void should_expose_the_unknown_code_in_NatureInconnue() {
        assertThatThrownBy(() -> NatureIntervenant.depuis("X")).isInstanceOfSatisfying(NatureInconnue.class,
                e -> assertThat(e.code()).isEqualTo("X"));
    }

    // ------------------------------------------------------------------ corriger (remplacement total, KB-GMAO-30)

    @Test
    void should_replace_nom_nature_coordonnees_and_tarif_keeping_identity_centre_and_state() {
        Intervenant intervenant = interne();
        intervenant.tirerEvenements();

        intervenant.corriger("Bernard Presta", NatureIntervenant.EXTERNE, "0666", "b@ext.test", tarif("150.75"), ADMIN, T1);

        assertThat(intervenant.nom()).isEqualTo("Bernard Presta");
        assertThat(intervenant.nature()).isEqualTo(NatureIntervenant.EXTERNE);
        assertThat(intervenant.telephone()).isEqualTo("0666");
        assertThat(intervenant.email()).isEqualTo("b@ext.test");
        assertThat(intervenant.tarif()).isEqualTo(tarif("150.75"));
        assertThat(intervenant.id()).isEqualTo(ID);
        assertThat(intervenant.centreId()).isEqualTo(CENTRE);
        assertThat(intervenant.estActif()).isTrue();
    }

    @Test
    void should_erase_omitted_coordonnees_and_tarif_on_correction() {
        Intervenant intervenant = interne();

        intervenant.corriger("Bernard", NatureIntervenant.INTERNE, null, null, null, ADMIN, T1);

        assertThat(intervenant.telephone()).isNull();
        assertThat(intervenant.email()).isNull();
        assertThat(intervenant.tarif()).isNull();
    }

    @Test
    void should_emit_CoordonneesIntervenantCorrigees_and_TarifIntervenantRevise_when_both_change() {
        Intervenant intervenant = interne();
        intervenant.tirerEvenements();

        intervenant.corriger("Nouveau", NatureIntervenant.INTERNE, "0555", "b@c.test", tarif("40"), ADMIN, T1);

        assertThat(intervenant.tirerEvenements()).containsExactly(
                new CoordonneesIntervenantCorrigees(ID, CENTRE, NatureIntervenant.INTERNE, ADMIN, T1),
                new TarifIntervenantRevise(ID, CENTRE, tarif("35.00"), tarif("40"), ADMIN, T1));
    }

    @Test
    void should_emit_only_TarifIntervenantRevise_when_only_the_tarif_changes() {
        Intervenant intervenant = interne();
        intervenant.tirerEvenements();

        intervenant.corriger("Bernard", NatureIntervenant.INTERNE, "0555", "b@c.test", null, ADMIN, T1);

        assertThat(intervenant.tirerEvenements()).containsExactly(
                new TarifIntervenantRevise(ID, CENTRE, tarif("35.00"), null, ADMIN, T1));
    }

    @Test
    void should_emit_only_CoordonneesIntervenantCorrigees_when_only_the_nature_changes() {
        Intervenant intervenant = interne();
        intervenant.tirerEvenements();

        intervenant.corriger("Bernard", NatureIntervenant.EXTERNE, "0555", "b@c.test", tarif("35.00"), ADMIN, T1);

        assertThat(intervenant.tirerEvenements()).containsExactly(
                new CoordonneesIntervenantCorrigees(ID, CENTRE, NatureIntervenant.EXTERNE, ADMIN, T1));
    }

    @Test
    void should_emit_nothing_but_keep_the_scale_received_when_the_tarif_value_is_unchanged() {
        Intervenant intervenant = interne();
        intervenant.tirerEvenements();

        intervenant.corriger("Bernard", NatureIntervenant.INTERNE, "0555", "b@c.test", tarif("35"), ADMIN, T1);

        assertThat(intervenant.tirerEvenements()).isEmpty();
        assertThat(intervenant.tarif().valeur()).hasToString("35");
    }

    @Test
    void should_detect_each_coordonnee_change_independently() {
        String[][] variantes = {{"Autre", "0555", "b@c.test"}, {"Bernard", "0000", "b@c.test"}, {"Bernard", "0555", "x@c.test"},
                {"Bernard", null, "b@c.test"}, {"Bernard", "0555", null}};
        for (String[] v : variantes) {
            Intervenant intervenant = interne();
            intervenant.tirerEvenements();
            intervenant.corriger(v[0], NatureIntervenant.INTERNE, v[1], v[2], tarif("35.00"), ADMIN, T1);
            assertThat(intervenant.tirerEvenements()).hasSize(1).first().isInstanceOf(CoordonneesIntervenantCorrigees.class);
        }
    }

    @Test
    void should_reject_correction_without_nom_or_nature_and_change_nothing() {
        Intervenant intervenant = interne();

        assertThatThrownBy(() -> intervenant.corriger(" ", NatureIntervenant.INTERNE, null, null, null, ADMIN, T1)).isInstanceOf(NomRequis.class);
        assertThatThrownBy(() -> intervenant.corriger("X", null, null, null, null, ADMIN, T1)).isInstanceOf(NatureRequise.class);

        assertThat(intervenant.nom()).isEqualTo("Bernard");
        assertThat(intervenant.tarif()).isEqualTo(tarif("35.00"));
    }

    @Test
    void should_keep_a_desactive_intervenant_desactive_when_corrected() {
        Intervenant intervenant = interne();
        intervenant.desactiver(ADMIN, T0);

        intervenant.corriger("Renomme", NatureIntervenant.INTERNE, null, null, tarif("0"), ADMIN, T1);

        assertThat(intervenant.estActif()).isFalse();
        assertThat(intervenant.nom()).isEqualTo("Renomme");
    }

    // ------------------------------------------------------------------ désactiver (R24, KB-GMAO-24/25)

    @Test
    void should_keep_intervenant_when_desactive_changing_only_the_state() {
        Intervenant intervenant = interne();
        intervenant.tirerEvenements();

        intervenant.desactiver(ADMIN, T1);

        assertThat(intervenant.estActif()).isFalse();
        assertThat(intervenant.nom()).isEqualTo("Bernard");
        assertThat(intervenant.nature()).isEqualTo(NatureIntervenant.INTERNE);
        assertThat(intervenant.tarif()).isEqualTo(tarif("35.00"));
        assertThat(intervenant.tirerEvenements()).containsExactly(new IntervenantDesactive(ID, CENTRE, ADMIN, T1));
    }

    @Test
    void should_be_idempotent_when_desactivated_twice_without_second_event() {
        Intervenant intervenant = interne();
        intervenant.desactiver(ADMIN, T0);
        intervenant.tirerEvenements();

        intervenant.desactiver(ADMIN, T1);

        assertThat(intervenant.estActif()).isFalse();
        assertThat(intervenant.tirerEvenements()).isEmpty();
    }

    @Test
    void should_drain_events_once() {
        Intervenant intervenant = interne();

        assertThat(intervenant.tirerEvenements()).hasSize(1);
        assertThat(intervenant.tirerEvenements()).isEmpty();
    }

    @Test
    void should_reconstitute_without_events_nor_rules() {
        Intervenant intervenant = Intervenant.reconstituer(ID, CENTRE, "X", NatureIntervenant.EXTERNE, null, null, null, false);

        assertThat(intervenant.estActif()).isFalse();
        assertThat(intervenant.tirerEvenements()).isEmpty();
    }

    @Test
    void should_reject_blank_ids() {
        assertThatThrownBy(() -> new IntervenantId(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
