package com.hemodialyse.backend.application.query;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Filtres « liste déroulante » (position, forfait) : ids sélectionnés, ou recherche par libellé en repli.
 */
class PatientListQueryServiceSelectedIdsTest {

    private static final UUID A = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID B = UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final UUID FROM_LABEL = UUID.fromString("20000000-0000-0000-0000-00000000000f");

    @Test
    void usesSelectedIdsAsIs() {
        assertThat(PatientListQueryService.selectedIdsOr(A + ", " + B, t -> Set.of(FROM_LABEL)))
                .containsExactlyInAnyOrder(A, B);
    }

    @Test
    void fallsBackToLabelSearchForFreeText() {
        assertThat(PatientListQueryService.selectedIdsOr("matin", t -> t.equals("matin") ? Set.of(FROM_LABEL) : Set.of()))
                .containsExactly(FROM_LABEL);
    }
}

