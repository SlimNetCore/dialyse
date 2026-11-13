package com.hemodialyse.backend.contexts.documents.application;

import com.hemodialyse.backend.contexts.documents.publishedlanguage.DeclarationDesTypesDeDocument;
import com.hemodialyse.backend.contexts.documents.publishedlanguage.TypeDeDocumentDeclare;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Le catalogue des types livrés est la réunion des types que les contextes déclarent, une seule source de vérité par type. */
class CatalogueDesTypesLivresTest {

    private static DeclarationDesTypesDeDocument declare(String... codes) {
        return () -> Arrays.stream(codes).map(c -> new TypeDeDocumentDeclare(c, "Libellé " + c, "reports/" + c + ".jrxml", List.of(), 1)).toList();
    }

    @Test
    void should_gather_the_declared_types_in_declaration_order() {
        var catalogue = new CatalogueDesTypesLivres(List.of(declare("A", "B"), declare("C")));

        assertThat(catalogue.types()).extracting(TypeDeDocumentDeclare::code).containsExactly("A", "B", "C");
    }

    @Test
    void should_find_a_type_ignoring_case_and_surrounding_spaces() {
        var catalogue = new CatalogueDesTypesLivres(List.of(declare("INVENTAIRE_STOCK")));

        assertThat(catalogue.trouver(" inventaire_stock ")).isPresent();
        assertThat(catalogue.trouver("INCONNU")).isEmpty();
        assertThat(catalogue.trouver(null)).isEmpty();
    }

    @Test
    void should_refuse_a_type_declared_twice() {
        assertThatThrownBy(() -> new CatalogueDesTypesLivres(List.of(declare("A"), declare("B", "A"))))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("A");
    }

    @Test
    void should_describe_a_model_by_its_label_when_the_declaration_has_no_description() {
        assertThat(new TypeDeDocumentDeclare("A", "Libellé A", "reports/a.jrxml", List.of(), 1).description()).isEqualTo("Libellé A");
        assertThat(new TypeDeDocumentDeclare("A", "Libellé A", "reports/a.jrxml", List.of(), 1, "Une description").description())
                .isEqualTo("Une description");
    }
}
