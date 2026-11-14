package com.hemodialyse.backend.contexts.stock.contrat;

import com.hemodialyse.backend.contexts.stock.publishedlanguage.BonsDeSortieDeLaReprise;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrat du contrôle des bons de sortie d'un patient repris (RP3b, ADR 0172), joué par le double en mémoire et par l'adapter JDBC (H2) :
 * les bons du patient de N'IMPORTE QUEL centre, jamais ceux d'un autre patient ni ceux sans patient.
 */
abstract class BonsDeSortieDeLaRepriseContrat {

    static final UUID CENTRE_A = new UUID(0, 0xA);
    static final UUID CENTRE_B = new UUID(0, 0xB);
    static final UUID PATIENT = new UUID(1, 1);
    static final UUID AUTRE_PATIENT = new UUID(1, 2);

    protected abstract BonsDeSortieDeLaReprise reprise();

    /** Un bon de sortie sous un centre, rattaché ou non à un patient. */
    protected abstract void bonDeSortie(UUID id, UUID centre, UUID patient);

    @Test
    void should_name_the_nature_the_cancellation_refusal_prints() {
        assertThat(BonsDeSortieDeLaReprise.NATURE).isEqualTo("bons_sortie");
    }

    @Test
    void should_list_the_issue_slips_of_the_patient_whatever_the_centre() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        bonDeSortie(a, CENTRE_A, PATIENT);
        bonDeSortie(b, CENTRE_B, PATIENT);
        bonDeSortie(UUID.randomUUID(), CENTRE_A, AUTRE_PATIENT);
        bonDeSortie(UUID.randomUUID(), CENTRE_A, null);

        assertThat(reprise().bonsDeSortieDuPatient(PATIENT)).as("tous centres, jamais un autre patient ni un bon sans patient")
                .containsExactlyInAnyOrder(a.toString(), b.toString());
    }

    @Test
    void should_list_nothing_for_a_patient_without_issue_slip() {
        bonDeSortie(UUID.randomUUID(), CENTRE_A, AUTRE_PATIENT);

        assertThat(reprise().bonsDeSortieDuPatient(PATIENT)).isEmpty();
    }
}
