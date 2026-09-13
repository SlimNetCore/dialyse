package com.hemodialyse.backend.domain.medical.examen.specification;

import com.hemodialyse.backend.domain.medical.examen.valueobject.StatutDemandeExamen;
import org.junit.jupiter.api.Test;

import static com.hemodialyse.backend.domain.medical.examen.valueobject.StatutDemandeExamen.ANNULE;
import static com.hemodialyse.backend.domain.medical.examen.valueobject.StatutDemandeExamen.DEMANDE;
import static com.hemodialyse.backend.domain.medical.examen.valueobject.StatutDemandeExamen.PRELEVE;
import static com.hemodialyse.backend.domain.medical.examen.valueobject.StatutDemandeExamen.RESULTAT_DISPONIBLE;
import static com.hemodialyse.backend.domain.medical.examen.valueobject.StatutDemandeExamen.VALIDE;
import static org.assertj.core.api.Assertions.assertThat;

class TransitionStatutDemandeSpecificationTest {

    @Test
    void demande_can_go_to_preleve_or_annule() {
        assertThat(TransitionStatutDemandeSpecification.estAutorisee(DEMANDE, PRELEVE)).isTrue();
        assertThat(TransitionStatutDemandeSpecification.estAutorisee(DEMANDE, ANNULE)).isTrue();
    }

    @Test
    void demande_cannot_skip_directly_to_valide() {
        assertThat(TransitionStatutDemandeSpecification.estAutorisee(DEMANDE, VALIDE)).isFalse();
        assertThat(TransitionStatutDemandeSpecification.estAutorisee(DEMANDE, RESULTAT_DISPONIBLE)).isFalse();
    }

    @Test
    void resultat_disponible_can_only_go_to_valide() {
        assertThat(TransitionStatutDemandeSpecification.estAutorisee(RESULTAT_DISPONIBLE, VALIDE)).isTrue();
        assertThat(TransitionStatutDemandeSpecification.estAutorisee(RESULTAT_DISPONIBLE, ANNULE)).isFalse();
    }

    @Test
    void terminal_states_allow_no_further_transition() {
        for (StatutDemandeExamen target : StatutDemandeExamen.values()) {
            assertThat(TransitionStatutDemandeSpecification.estAutorisee(VALIDE, target)).isFalse();
            assertThat(TransitionStatutDemandeSpecification.estAutorisee(ANNULE, target)).isFalse();
        }
    }
}
