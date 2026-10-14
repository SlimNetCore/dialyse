package com.hemodialyse.backend.contexts.gmao.domain.equipement;

import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** R7 : un code est unique dans son centre, comparé exactement (KB-GMAO-33). */
class UniciteDuCodeEquipementTest {

    private static final CentreId CENTRE = new CentreId(UUID.fromString("00000000-0000-0000-0000-0000000000a1"));
    private static final CentreId AUTRE_CENTRE = new CentreId(UUID.fromString("00000000-0000-0000-0000-0000000000a2"));

    private final Set<String> utilises = new HashSet<>(Set.of(CENTRE.valeur() + "|G-01"));
    private final UniciteDuCodeEquipement unicite =
            new UniciteDuCodeEquipement((centre, code) -> utilises.contains(centre.valeur() + "|" + code.valeur()));

    @Test
    void should_refuse_a_code_already_used_in_the_centre_with_the_legacy_message() {
        assertThatThrownBy(() -> unicite.verifier(CENTRE, new CodeEquipement("G-01")))
                .isInstanceOf(CodeEquipementDejaUtilise.class)
                .hasMessage("Un équipement avec ce code existe déjà dans ce centre");
    }

    @Test
    void should_accept_a_code_used_in_another_centre() {
        assertThatCode(() -> unicite.verifier(AUTRE_CENTRE, new CodeEquipement("G-01"))).doesNotThrowAnyException();
    }

    @Test
    void should_compare_codes_exactly_without_trimming_or_case_folding() {
        for (String variante : new String[]{"g-01", " G-01", "G-01 ", "G -01"}) {
            assertThatCode(() -> unicite.verifier(CENTRE, new CodeEquipement(variante))).doesNotThrowAnyException();
        }
    }
}
