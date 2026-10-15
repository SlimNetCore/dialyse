package com.hemodialyse.backend.contexts.comptabilite.contrat;

import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;
import com.hemodialyse.backend.contexts.comptabilite.domain.journal.CodeJournal;
import com.hemodialyse.backend.contexts.comptabilite.domain.journal.Journal;
import com.hemodialyse.backend.contexts.comptabilite.domain.journal.JournalRepository;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrat partagé des journaux d'un centre : exécuté contre le double en mémoire, l'adaptateur JPA sur H2 et l'adaptateur sur
 * PostgreSQL 16.
 */
public abstract class JournalContrat {

    protected static final CentreId CENTRE_A = new CentreId(UUID.fromString("99996300-0000-0000-0000-00000000000a"));
    protected static final CentreId CENTRE_B = new CentreId(UUID.fromString("99996300-0000-0000-0000-00000000000b"));

    protected abstract JournalRepository repository();

    protected void journal(CentreId centre, String code, String libelle, boolean actif) {
        repository().enregistrer(Journal.reconstitue(centre, CodeJournal.de(code), libelle, actif));
    }

    @Test
    void should_find_a_saved_journal_with_its_label_and_state() {
        journal(CENTRE_A, "OD", "Opérations diverses", false);

        Journal trouve = repository().trouver(CENTRE_A, CodeJournal.de("od")).orElseThrow();

        assertThat(trouve.code().valeur()).isEqualTo("OD");
        assertThat(trouve.libelle()).isEqualTo("Opérations diverses");
        assertThat(trouve.actif()).isFalse();
        assertThat(trouve.centre()).isEqualTo(CENTRE_A);
    }

    @Test
    void should_replace_label_and_state_when_saving_the_same_code_again() {
        journal(CENTRE_A, "OD", "Opérations diverses", true);
        journal(CENTRE_A, "OD", "Divers", false);

        assertThat(repository().duCentre(CENTRE_A)).hasSize(1);
        Journal trouve = repository().trouver(CENTRE_A, CodeJournal.de("OD")).orElseThrow();
        assertThat(trouve.libelle()).isEqualTo("Divers");
        assertThat(trouve.actif()).isFalse();
    }

    @Test
    void should_list_the_journals_of_a_centre_sorted_by_code() {
        journal(CENTRE_A, "VE", "Ventes", true);
        journal(CENTRE_A, "AC", "Achats", true);
        journal(CENTRE_A, "OD", "Divers", false);
        journal(CENTRE_B, "BQ", "Autre centre", true);

        assertThat(repository().duCentre(CENTRE_A)).extracting(j -> j.code().valeur()).containsExactly("AC", "OD", "VE");
        assertThat(repository().duCentre(CENTRE_B)).extracting(j -> j.code().valeur()).containsExactly("BQ");
    }

    @Test
    void should_return_no_journal_for_a_centre_that_saved_nothing() {
        assertThat(repository().duCentre(CENTRE_A)).isEmpty();
        assertThat(repository().trouver(CENTRE_A, CodeJournal.VE)).isEmpty();
    }

    @Test
    void should_keep_the_same_code_in_two_centres_apart() {
        journal(CENTRE_A, "VE", "Ventes A", true);
        journal(CENTRE_B, "VE", "Ventes B", false);

        assertThat(repository().trouver(CENTRE_A, CodeJournal.VE).orElseThrow().libelle()).isEqualTo("Ventes A");
        assertThat(repository().trouver(CENTRE_B, CodeJournal.VE).orElseThrow().libelle()).isEqualTo("Ventes B");
    }

    @Test
    void should_delete_a_journal_in_its_centre_only_and_ignore_an_unknown_one() {
        journal(CENTRE_A, "VE", "Ventes A", true);
        journal(CENTRE_B, "VE", "Ventes B", true);

        repository().supprimer(CENTRE_A, CodeJournal.VE);
        repository().supprimer(CENTRE_A, CodeJournal.VE);

        assertThat(repository().trouver(CENTRE_A, CodeJournal.VE)).isEmpty();
        assertThat(repository().trouver(CENTRE_B, CodeJournal.VE)).isPresent();
    }
}
