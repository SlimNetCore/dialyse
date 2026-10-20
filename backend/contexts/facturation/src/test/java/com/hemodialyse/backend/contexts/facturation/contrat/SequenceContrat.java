package com.hemodialyse.backend.contexts.facturation.contrat;

import com.hemodialyse.backend.contexts.facturation.domain.CentreId;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.SequenceDeFacturation;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrat partagé de la séquence de numérotation (lecture) : double en mémoire, JDBC sur H2 et PostgreSQL 16.
 */
public abstract class SequenceContrat {

    protected static final CentreId CENTRE_A = new CentreId(UUID.fromString("99996800-0000-0000-0000-0000000000a1"));
    protected static final CentreId CENTRE_B = new CentreId(UUID.fromString("99996800-0000-0000-0000-0000000000b1"));

    protected abstract SequenceDeFacturation sequence();

    protected abstract void poser(CentreId centre, int annee, int valeur);

    @Test
    void should_be_zero_for_a_centre_that_issued_nothing() {
        assertThat(sequence().courante(CENTRE_A, 2026)).isZero();
    }

    @Test
    void should_read_the_current_value_of_the_year() {
        poser(CENTRE_A, 2026, 41);

        assertThat(sequence().courante(CENTRE_A, 2026)).isEqualTo(41);
    }

    @Test
    void should_keep_the_years_and_the_centres_apart() {
        poser(CENTRE_A, 2025, 7);
        poser(CENTRE_A, 2026, 41);
        poser(CENTRE_B, 2026, 99);

        assertThat(sequence().courante(CENTRE_A, 2025)).isEqualTo(7);
        assertThat(sequence().courante(CENTRE_A, 2026)).isEqualTo(41);
        assertThat(sequence().courante(CENTRE_B, 2026)).isEqualTo(99);
        assertThat(sequence().courante(CENTRE_B, 2025)).isZero();
    }
}
