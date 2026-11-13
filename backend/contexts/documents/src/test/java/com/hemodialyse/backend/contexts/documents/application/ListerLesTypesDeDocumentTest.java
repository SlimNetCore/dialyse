package com.hemodialyse.backend.contexts.documents.application;

import com.hemodialyse.backend.contexts.documents.domain.TypeDisponible;
import com.hemodialyse.backend.contexts.documents.infrastructure.declaration.TypesLivresDeDocuments;
import com.hemodialyse.backend.contexts.documents.publishedlanguage.DeclarationDesTypesDeDocument;
import com.hemodialyse.backend.contexts.documents.publishedlanguage.TypeDeDocumentDeclare;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Les types de document proposés : ceux que les contextes déclarent, puis le rapport personnalisé. */
class ListerLesTypesDeDocumentTest {

    private static DeclarationDesTypesDeDocument declare(String... codes) {
        return () -> java.util.Arrays.stream(codes).map(c -> new TypeDeDocumentDeclare(c, "Libellé " + c, "reports/" + c + ".jrxml", List.of(), 1)).toList();
    }

    @Test
    void should_offer_the_declared_types_in_declaration_order_then_the_custom_report() {
        var types = new ListerLesTypesDeDocument(List.of(declare("A", "B"), declare("C"))).lister();

        assertThat(types).extracting(TypeDisponible::code).containsExactly("A", "B", "C", "CUSTOM");
        assertThat(types.get(0).libelle()).isEqualTo("Libellé A");
        assertThat(types.get(3).libelle()).isEqualTo("Rapport personnalisé");
    }

    @Test
    void should_offer_only_the_custom_report_when_nobody_declares_a_type() {
        assertThat(new ListerLesTypesDeDocument(List.of()).lister()).containsExactly(TypeDisponible.rapportPersonnalise());
    }

    @Test
    void should_refuse_a_type_declared_twice() {
        assertThatThrownBy(() -> new ListerLesTypesDeDocument(List.of(declare("A"), declare("A"))))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("A");
    }

    @Test
    void should_reserve_the_custom_code_to_documents() {
        assertThatThrownBy(() -> new ListerLesTypesDeDocument(List.of(declare("CUSTOM")))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void should_declare_the_six_documents_of_the_patient_in_their_historical_order() {
        var livres = new TypesLivresDeDocuments().types();

        assertThat(livres).extracting(TypeDeDocumentDeclare::code).containsExactly("FICHE_PATIENT", "ATTESTATION", "PEC", "LISTE_PATIENTS",
                "LISTE_PEC", "LISTE_ATTESTATIONS");
        assertThat(livres).allSatisfy(t -> assertThat(t.description()).isNotBlank().isNotEqualTo(t.libelle()));
        assertThat(new ListerLesTypesDeDocument(List.of(new TypesLivresDeDocuments())).lister()).hasSize(livres.size() + 1);
    }

    @Test
    void should_refuse_a_type_without_label_or_with_a_lowercase_code() {
        assertThatThrownBy(() -> new TypeDisponible("X", " ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TypeDisponible(" ", "L")).isInstanceOf(IllegalArgumentException.class);
    }
}
