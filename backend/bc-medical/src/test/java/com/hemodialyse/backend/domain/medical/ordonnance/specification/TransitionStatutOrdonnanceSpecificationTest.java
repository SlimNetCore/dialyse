package com.hemodialyse.backend.domain.medical.ordonnance.specification;

import com.hemodialyse.backend.domain.medical.ordonnance.valueobject.StatutOrdonnance;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TransitionStatutOrdonnanceSpecificationTest {

    @Test
    void brouillon_should_allow_signature_and_annulation() {
        assertThat(TransitionStatutOrdonnanceSpecification.estAutorisee(
                StatutOrdonnance.BROUILLON, StatutOrdonnance.SIGNEE)).isTrue();
        assertThat(TransitionStatutOrdonnanceSpecification.estAutorisee(
                StatutOrdonnance.BROUILLON, StatutOrdonnance.ANNULEE)).isTrue();
    }

    @Test
    void brouillon_should_not_jump_to_imprimee() {
        assertThat(TransitionStatutOrdonnanceSpecification.estAutorisee(
                StatutOrdonnance.BROUILLON, StatutOrdonnance.IMPRIMEE)).isFalse();
    }

    @Test
    void signee_should_allow_impression_and_annulation() {
        assertThat(TransitionStatutOrdonnanceSpecification.estAutorisee(
                StatutOrdonnance.SIGNEE, StatutOrdonnance.IMPRIMEE)).isTrue();
        assertThat(TransitionStatutOrdonnanceSpecification.estAutorisee(
                StatutOrdonnance.SIGNEE, StatutOrdonnance.ANNULEE)).isTrue();
    }

    @Test
    void signee_should_not_return_to_brouillon() {
        assertThat(TransitionStatutOrdonnanceSpecification.estAutorisee(
                StatutOrdonnance.SIGNEE, StatutOrdonnance.BROUILLON)).isFalse();
    }

    @Test
    void imprimee_should_still_allow_annulation() {
        assertThat(TransitionStatutOrdonnanceSpecification.estAutorisee(
                StatutOrdonnance.IMPRIMEE, StatutOrdonnance.ANNULEE)).isTrue();
    }

    @Test
    void imprimee_should_not_allow_re_signature() {
        assertThat(TransitionStatutOrdonnanceSpecification.estAutorisee(
                StatutOrdonnance.IMPRIMEE, StatutOrdonnance.SIGNEE)).isFalse();
    }

    @Test
    void annulee_is_terminal() {
        assertThat(TransitionStatutOrdonnanceSpecification.estAutorisee(
                StatutOrdonnance.ANNULEE, StatutOrdonnance.BROUILLON)).isFalse();
        assertThat(TransitionStatutOrdonnanceSpecification.estAutorisee(
                StatutOrdonnance.ANNULEE, StatutOrdonnance.SIGNEE)).isFalse();
    }
}
