package com.hemodialyse.backend.contexts.documents.infrastructure.rendu;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Seuls les gabarits livrés, par un nom de fichier sûr, sont lisibles : jamais un chemin disque ni un chemin qui remonte. */
class GabaritsLivresDuClasspathTest {

    private final GabaritsLivresDuClasspath livres = new GabaritsLivresDuClasspath();

    @Test
    void should_read_a_delivered_template_by_its_model_path() {
        assertThat(livres.source("reports/attestation.jrxml")).hasValueSatisfying(s -> assertThat(s).contains("<jasperReport"));
        assertThat(livres.source("autre/dossier/attestation.jrxml")).isPresent();
        assertThat(livres.source("reports\\attestation.jrxml")).isPresent();
    }

    @Test
    void should_give_nothing_for_a_missing_template_or_a_doubtful_path() {
        assertThat(livres.source("reports/inconnu.jrxml")).isEmpty();
        assertThat(livres.source("../../etc/passwd")).isEmpty();
        assertThat(livres.source("reports/a b.jrxml")).isEmpty();
        assertThat(livres.source("reports/attestation.txt")).isEmpty();
        assertThat(livres.source("reports/")).isEmpty();
        assertThat(livres.source("")).isEmpty();
        assertThat(livres.source(null)).isEmpty();
    }

    @Test
    void should_keep_only_a_safe_file_name() {
        assertThat(GabaritsLivresDuClasspath.nomSur("reports/ab_C-1.jrxml")).isEqualTo("ab_C-1.jrxml");
        assertThat(GabaritsLivresDuClasspath.nomSur("a/..\\b.jrxml")).isEqualTo("b.jrxml");
        assertThat(GabaritsLivresDuClasspath.nomSur("x/é.jrxml")).isNull();
    }
}
