package com.hemodialyse.backend.contexts.planification.domain;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RG-PLN-060 : un générateur de secours pour huit en service, arrondi au supérieur.
 */
class GenerateursDeSecoursTest {

    @ParameterizedTest(name = "{0} en service -> {1} de secours")
    @CsvSource({"-1,0", "0,0", "1,1", "7,1", "8,1", "9,2", "16,2", "17,3"})
    void should_reserve_one_backup_generator_per_started_group_of_eight(int enService, int secours) {
        assertThat(GenerateursDeSecours.pour(enService)).isEqualTo(secours);
    }
}
