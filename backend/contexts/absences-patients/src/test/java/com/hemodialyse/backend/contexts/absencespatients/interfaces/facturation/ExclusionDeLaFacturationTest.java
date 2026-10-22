package com.hemodialyse.backend.contexts.absencespatients.interfaces.facturation;

import com.hemodialyse.backend.contexts.absencespatients.domain.MotifAbsence;
import com.hemodialyse.backend.contexts.facturation.domain.seance.MotifDAbsence;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Table de traduction des motifs de la Facturation vers ceux des Absences : chaque motif a son homologue de même nom, et les
 * Absences n'en ont pas d'autre (un motif ajouté d'un côté seulement fait échouer ce test).
 */
class ExclusionDeLaFacturationTest {

    @ParameterizedTest
    @EnumSource(MotifDAbsence.class)
    void chaque_motif_de_la_facturation_a_son_homologue_de_meme_nom(MotifDAbsence motif) {
        MotifAbsence traduit = ExclusionDeLaFacturation.motifDesAbsences(motif.name());

        assertThat(traduit.name()).isEqualTo(motif.name());
        assertThat(traduit.commentaireRequis()).isEqualTo(motif.commentaireRequis());
    }

    @org.junit.jupiter.api.Test
    void les_deux_contextes_ont_les_memes_motifs() {
        Set<String> facturation = Arrays.stream(MotifDAbsence.values()).map(Enum::name).collect(Collectors.toSet());
        Set<String> absences = Arrays.stream(MotifAbsence.values()).map(Enum::name).collect(Collectors.toSet());

        assertThat(facturation).isEqualTo(absences);
    }
}
