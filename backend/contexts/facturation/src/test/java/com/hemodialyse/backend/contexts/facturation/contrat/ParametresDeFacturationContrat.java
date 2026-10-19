package com.hemodialyse.backend.contexts.facturation.contrat;

import com.hemodialyse.backend.contexts.facturation.domain.CentreId;
import com.hemodialyse.backend.contexts.facturation.domain.parametres.ParametresDeFacturation;
import com.hemodialyse.backend.contexts.facturation.domain.parametres.ParametresDeFacturationRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrat partagé des paramètres de facturation : exécuté contre le double en mémoire, l'adaptateur JDBC sur H2 et
 * l'adaptateur sur PostgreSQL 16 (où la création comme le remplacement passent, KB-FAC-07).
 */
public abstract class ParametresDeFacturationContrat {

    protected static final CentreId CENTRE_A = new CentreId(UUID.fromString("99996700-0000-0000-0000-00000000000a"));
    protected static final CentreId CENTRE_B = new CentreId(UUID.fromString("99996700-0000-0000-0000-00000000000b"));
    private static final Instant LE_19 = Instant.parse("2026-10-19T10:00:00Z");
    private static final Instant LE_20 = Instant.parse("2026-10-20T11:30:00Z");

    protected abstract ParametresDeFacturationRepository repository();

    @Test
    void should_have_nothing_for_a_centre_that_saved_nothing() {
        assertThat(repository().duCentre(CENTRE_A)).isEmpty();
    }

    @Test
    void should_create_then_read_back_the_settings_of_a_centre() {
        repository().enregistrer(CENTRE_A, ParametresDeFacturation.saisis("F{YY}-{CENTER}-{SEQ6}", false, LE_19), "admin");

        ParametresDeFacturation relus = repository().duCentre(CENTRE_A).orElseThrow();
        assertThat(relus.modele().valeur()).isEqualTo("F{YY}-{CENTER}-{SEQ6}");
        assertThat(relus.regroupementMultiForfait()).isFalse();
        assertThat(relus.misAJourLe()).isEqualTo(LE_19);
    }

    @Test
    void should_replace_the_settings_already_saved() {
        repository().enregistrer(CENTRE_A, ParametresDeFacturation.saisis("A-{SEQ}", false, LE_19), "admin");
        repository().enregistrer(CENTRE_A, ParametresDeFacturation.saisis("B-{SEQ}", true, LE_20), "autre");

        ParametresDeFacturation relus = repository().duCentre(CENTRE_A).orElseThrow();
        assertThat(relus.modele().valeur()).isEqualTo("B-{SEQ}");
        assertThat(relus.regroupementMultiForfait()).isTrue();
        assertThat(relus.misAJourLe()).isEqualTo(LE_20);
    }

    @Test
    void should_keep_the_settings_of_two_centres_apart() {
        repository().enregistrer(CENTRE_A, ParametresDeFacturation.saisis("A-{SEQ}", false, LE_19), "admin");

        assertThat(repository().duCentre(CENTRE_B)).isEmpty();
        repository().enregistrer(CENTRE_B, ParametresDeFacturation.saisis("B-{SEQ}", true, LE_20), "admin");
        assertThat(repository().duCentre(CENTRE_A).orElseThrow().modele().valeur()).isEqualTo("A-{SEQ}");
    }
}
