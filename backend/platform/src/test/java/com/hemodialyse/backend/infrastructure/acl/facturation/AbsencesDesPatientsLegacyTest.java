package com.hemodialyse.backend.infrastructure.acl.facturation;

import com.hemodialyse.backend.contexts.facturation.domain.seance.MotifDAbsence;
import com.hemodialyse.backend.domain.absence.model.MotifAbsence;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Table de traduction des motifs de la Facturation vers ceux du module Absences : chaque motif a son homologue de même nom, et
 * le module Absences n'en a pas d'autre (un motif ajouté d'un côté seulement doit faire échouer ce test).
 */
class AbsencesDesPatientsLegacyTest {

    @ParameterizedTest
    @EnumSource(MotifDAbsence.class)
    void should_translate_each_reason_to_the_absences_module_reason_of_the_same_name(MotifDAbsence motif) {
        MotifAbsence traduit = AbsencesDesPatientsLegacy.motifDuModuleAbsences(motif);

        assertThat(traduit.name()).isEqualTo(motif.name());
        assertThat(traduit.commentaireRequis()).isEqualTo(motif.commentaireRequis());
    }

    @org.junit.jupiter.api.Test
    void should_cover_all_the_reasons_of_the_absences_module() {
        Set<String> facturation = Arrays.stream(MotifDAbsence.values()).map(Enum::name).collect(Collectors.toSet());
        Set<String> absences = Arrays.stream(MotifAbsence.values()).map(Enum::name).collect(Collectors.toSet());

        assertThat(facturation).isEqualTo(absences);
    }
}
