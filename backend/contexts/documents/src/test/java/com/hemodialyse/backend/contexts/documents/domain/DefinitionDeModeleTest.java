package com.hemodialyse.backend.contexts.documents.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Les règles d'un modèle de document : gabarit livré uniquement, champs obligatoires et bornes des colonnes, recherche paginée bornée. */
class DefinitionDeModeleTest {

    private static final UUID CENTRE = new UUID(0L, 0xdL);

    private static DefinitionDeModele avecChemin(String chemin) {
        return DefinitionDeModele.definir(CENTRE, "ATT", "Attestation", "ATTESTATION", chemin, "PDF", null, true);
    }

    @ParameterizedTest
    @ValueSource(strings = {"reports/attestation.jrxml", "reports/A-b_9.jrxml", "reports/0.jrxml"})
    void should_accept_a_template_delivered_with_the_application(String chemin) {
        assertThat(CheminDeGabarit.de(chemin).valeur()).isEqualTo(chemin);
        assertThat(avecChemin(chemin).gabarit().valeur()).isEqualTo(chemin);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "../../etc/passwd", "reports/../secret.jrxml", "reports/a/b.jrxml", "reports/a.jrxml.exe", "reports/.jrxml",
            "reports/é.jrxml", "/etc/reports/a.jrxml", "reports/a b.jrxml", "REPORTS/a.jrxml", "reports/a.JRXML", "reports/a.jrxml\n",
            "classpath:reports/a.jrxml", "file:///etc/passwd"})
    void should_refuse_any_path_that_is_not_a_delivered_template(String chemin) {
        assertThatThrownBy(() -> avecChemin(chemin)).isInstanceOf(ModeleRefuse.class)
                .hasMessage("Chemin de modèle invalide : attendu reports/<nom>.jrxml");
    }

    @Test
    void should_check_the_template_path_before_the_other_fields() {
        assertThatThrownBy(() -> DefinitionDeModele.definir(CENTRE, null, null, null, "../x", null, null, null))
                .hasMessage("Chemin de modèle invalide : attendu reports/<nom>.jrxml");
    }

    @Test
    void should_refuse_a_missing_required_field() {
        assertThatThrownBy(() -> DefinitionDeModele.definir(CENTRE, null, "L", "T", "reports/a.jrxml", "PDF", null, true)).hasMessage("Champ obligatoire : code");
        assertThatThrownBy(() -> DefinitionDeModele.definir(CENTRE, "C", null, "T", "reports/a.jrxml", "PDF", null, true)).hasMessage("Champ obligatoire : libelle");
        assertThatThrownBy(() -> DefinitionDeModele.definir(CENTRE, "C", "L", null, "reports/a.jrxml", "PDF", null, true)).hasMessage("Champ obligatoire : typeDocument");
        assertThatThrownBy(() -> DefinitionDeModele.definir(CENTRE, "C", "L", "T", "reports/a.jrxml", null, null, true)).hasMessage("Champ obligatoire : formatImpression");
        assertThatThrownBy(() -> DefinitionDeModele.definir(CENTRE, "C", "L", "T", "reports/a.jrxml", "PDF", null, null)).hasMessage("Champ obligatoire : active");
    }

    @Test
    void should_accept_the_limits_of_the_columns_and_refuse_one_character_more() {
        assertThat(DefinitionDeModele.definir(CENTRE, "c".repeat(50), "l".repeat(255), "t".repeat(50), "reports/a.jrxml", "f".repeat(20), "d".repeat(500), true))
                .isNotNull();
        assertThatThrownBy(() -> DefinitionDeModele.definir(CENTRE, "c".repeat(51), "L", "T", "reports/a.jrxml", "PDF", null, true))
                .hasMessage("Champ trop long : code (50 caractères au plus)");
        assertThatThrownBy(() -> DefinitionDeModele.definir(CENTRE, "C", "l".repeat(256), "T", "reports/a.jrxml", "PDF", null, true))
                .hasMessage("Champ trop long : libelle (255 caractères au plus)");
        assertThatThrownBy(() -> DefinitionDeModele.definir(CENTRE, "C", "L", "t".repeat(51), "reports/a.jrxml", "PDF", null, true))
                .hasMessage("Champ trop long : typeDocument (50 caractères au plus)");
        assertThatThrownBy(() -> DefinitionDeModele.definir(CENTRE, "C", "L", "T", "reports/a.jrxml", "f".repeat(21), null, true))
                .hasMessage("Champ trop long : formatImpression (20 caractères au plus)");
        assertThatThrownBy(() -> DefinitionDeModele.definir(CENTRE, "C", "L", "T", "reports/a.jrxml", "PDF", "d".repeat(501), true))
                .hasMessage("Champ trop long : description (500 caractères au plus)");
    }

    @Test
    void should_keep_reproducing_blank_codes_and_free_types_and_formats_until_the_business_decides() {
        DefinitionDeModele modele = DefinitionDeModele.definir(CENTRE, "  ", "", "ordonnance", "reports/a.jrxml", "WORD", null, false);

        assertThat(modele.code()).isBlank();
        assertThat(modele.type()).isEqualTo("ordonnance");
        assertThat(modele.format()).isEqualTo("WORD");
        assertThat(modele.actif()).isFalse();
    }

    @Test
    void should_search_a_page_with_a_size_from_1_to_200_and_never_adjust_it() {
        assertThat(RechercheDeModeles.de(CENTRE, null, 0, 200).decalage()).isZero();
        assertThat(RechercheDeModeles.de(CENTRE, "CUSTOM", 3, 20).decalage()).isEqualTo(60L);
        assertThat(RechercheDeModeles.de(CENTRE, null, Integer.MAX_VALUE, 200).decalage()).isEqualTo((long) Integer.MAX_VALUE * 200);
        assertThatThrownBy(() -> RechercheDeModeles.de(CENTRE, null, 0, 0)).isInstanceOf(ModeleRefuse.class);
        assertThatThrownBy(() -> RechercheDeModeles.de(CENTRE, null, 0, 201)).isInstanceOf(ModeleRefuse.class);
        assertThatThrownBy(() -> RechercheDeModeles.de(CENTRE, null, -1, 10)).isInstanceOf(ModeleRefuse.class);
        assertThatThrownBy(() -> RechercheDeModeles.de(CENTRE, null, null, 10)).hasMessage("Champ obligatoire : page");
        assertThatThrownBy(() -> RechercheDeModeles.de(CENTRE, null, 0, null)).hasMessage("Champ obligatoire : size");
    }
}
