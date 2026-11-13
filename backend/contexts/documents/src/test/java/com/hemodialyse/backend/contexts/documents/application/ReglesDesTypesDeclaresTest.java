package com.hemodialyse.backend.contexts.documents.application;

import com.hemodialyse.backend.contexts.documents.publishedlanguage.RegleDImpressionDUnType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Documents orchestre les règles publiées par les contextes propriétaires : par type, par modèle, données du serveur. */
class ReglesDesTypesDeclaresTest {

    private static final UUID CENTRE = UUID.fromString("00000000-0000-0000-0000-00000000000d");

    private static final class Regle implements RegleDImpressionDUnType {
        final String type;
        final boolean parLeModele;
        final List<String> appels = new ArrayList<>();
        RuntimeException refus;

        Regle(String type, boolean parLeModele) {
            this.type = type;
            this.parLeModele = parLeModele;
        }

        @Override
        public String type() {
            return type;
        }

        @Override
        public void garder(UUID centreDemande) {
            appels.add("garde:" + centreDemande);
            if (refus != null) throw refus;
        }

        @Override
        public boolean gardeAussiParLeModele() {
            return parLeModele;
        }

        @Override
        public Map<String, Object> donneesDuServeur(UUID centre, Map<String, String> parametres) {
            return Map.of("DU_TYPE", type);
        }
    }

    @Test
    void should_guard_by_type_ignoring_case_and_surrounding_spaces() {
        var ordonnance = new Regle("ORDONNANCE", false);
        ordonnance.refus = new IllegalStateException("refus");
        var regles = new ReglesDesTypesDeclares(List.of(ordonnance));

        assertThatThrownBy(() -> regles.garderParType("  ordonnance ", CENTRE)).hasMessage("refus");
        assertThat(ordonnance.appels).containsExactly("garde:" + CENTRE);
    }

    @Test
    void should_leave_a_type_without_rule_unguarded_and_without_server_data() {
        var regles = new ReglesDesTypesDeclares(List.of(new Regle("ORDONNANCE", false)));

        regles.garderParType("LISTE_PATIENTS", CENTRE);
        regles.garderParType(null, null);
        regles.garderParModele(null, CENTRE);
        assertThat(regles.donneesDuServeur("LISTE_PATIENTS", CENTRE, Map.of())).isEmpty();
        assertThat(regles.donneesDuServeur(null, CENTRE, Map.of())).isEmpty();
    }

    @Test
    void should_guard_by_model_only_when_the_rule_asks_for_it() {
        var ordonnance = new Regle("ORDONNANCE", false);
        var synthese = new Regle("SYNTHESE_FACTURATION_MENSUELLE", true);
        var regles = new ReglesDesTypesDeclares(List.of(ordonnance, synthese));

        regles.garderParModele("ORDONNANCE", CENTRE);
        regles.garderParModele("synthese_facturation_mensuelle", CENTRE);

        assertThat(ordonnance.appels).isEmpty();
        assertThat(synthese.appels).containsExactly("garde:" + CENTRE);
    }

    @Test
    void should_take_server_data_from_the_rule_of_the_type() {
        var regles = new ReglesDesTypesDeclares(List.of(new Regle("ORDONNANCE", false), new Regle("SYNTHESE_FACTURATION_MENSUELLE", true)));

        assertThat(regles.donneesDuServeur("synthese_facturation_mensuelle ", CENTRE, Map.of())).containsEntry("DU_TYPE", "SYNTHESE_FACTURATION_MENSUELLE");
    }

    @Test
    void should_refuse_two_rules_for_the_same_type_or_a_rule_without_type() {
        assertThatThrownBy(() -> new ReglesDesTypesDeclares(List.of(new Regle("ORDONNANCE", false), new Regle(" ordonnance", true))))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("ORDONNANCE");
        assertThatThrownBy(() -> new ReglesDesTypesDeclares(List.of(new Regle(" ", false)))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new ReglesDesTypesDeclares(List.of(new Regle(null, false)))).isInstanceOf(IllegalStateException.class);
    }
}
