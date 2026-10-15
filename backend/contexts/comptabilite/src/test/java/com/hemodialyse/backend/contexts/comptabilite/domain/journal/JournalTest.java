package com.hemodialyse.backend.contexts.comptabilite.domain.journal;

import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;
import com.hemodialyse.backend.contexts.comptabilite.doubles.UsagesEnMemoire;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JournalTest {

    private static final CentreId CENTRE = new CentreId(UUID.randomUUID());
    private static final CodeJournal OD = CodeJournal.de("OD");

    private final UsagesEnMemoire usages = new UsagesEnMemoire();

    private Journal ouvert() {
        return Journal.ouvrir(CENTRE, OD, "Opérations diverses", true, usages);
    }

    @Test
    void les_cinq_journaux_par_defaut_sont_ventes_banque_caisse_achats_stocks() {
        List<Journal> defauts = Journal.parDefaut(CENTRE);

        assertThat(defauts).extracting(j -> j.code().valeur()).containsExactly("VE", "BQ", "CA", "AC", "ST");
        assertThat(defauts).extracting(Journal::libelle).containsExactly("Ventes", "Banque", "Caisse", "Achats", "Stocks");
        assertThat(defauts).allMatch(Journal::actif).allMatch(j -> j.centre().equals(CENTRE));
    }

    @Test
    void le_libelle_est_obligatoire_rogne_et_limite_a_100_caracteres() {
        for (String libelle : new String[]{null, "", "  "}) {
            assertThatThrownBy(() -> Journal.ouvrir(CENTRE, OD, libelle, true, usages))
                    .isInstanceOf(IllegalArgumentException.class).hasMessage("Le libellé du journal est obligatoire");
        }
        assertThat(Journal.ouvrir(CENTRE, OD, "  Rogné ", true, usages).libelle()).isEqualTo("Rogné");
        assertThat(Journal.ouvrir(CENTRE, OD, "é".repeat(100), true, usages).libelle()).hasSize(100);
        assertThatThrownBy(() -> Journal.ouvrir(CENTRE, OD, "é".repeat(101), true, usages))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Le libellé du journal dépasse 100 caractères");
    }

    @Test
    void le_code_est_obligatoire() {
        assertThatThrownBy(() -> Journal.reconstitue(CENTRE, null, "Sans code", true))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Le code du journal est obligatoire");
    }

    @Test
    void un_journal_libre_se_desactive_et_se_reactive() {
        Journal journal = ouvert();

        journal.redefinir("Ancien", false, usages);
        assertThat(journal.actif()).isFalse();
        assertThat(journal.libelle()).isEqualTo("Ancien");

        journal.redefinir("Opérations diverses", true, usages);
        assertThat(journal.actif()).isTrue();
    }

    @Test
    void un_journal_choisi_pour_une_operation_ou_un_modele_ne_se_desactive_pas() {
        usages.journalChoisi("OD");
        Journal journal = ouvert();

        assertThatThrownBy(() -> journal.redefinir("Opérations diverses", false, usages)).isInstanceOf(JournalUtilise.class)
                .hasMessage("Le journal OD est choisi pour une opération : changez-la avant de le désactiver")
                .extracting("code").isEqualTo("JOURNAL_UTILISE");
        assertThat(journal.actif()).isTrue();
        assertThatThrownBy(() -> Journal.ouvrir(CENTRE, OD, "Opérations diverses", false, usages))
                .isInstanceOf(JournalUtilise.class);
    }

    @Test
    void un_journal_utilise_peut_changer_de_libelle_tant_qu_il_reste_actif() {
        usages.journalChoisi("OD");
        Journal journal = ouvert();

        journal.redefinir("OD renommé", true, usages);

        assertThat(journal.libelle()).isEqualTo("OD renommé");
    }

    @Test
    void un_journal_libre_se_supprime() {
        assertThatCode(() -> ouvert().exigerSuppressible(usages)).doesNotThrowAnyException();
    }

    @Test
    void un_journal_utilise_ne_se_supprime_pas_meme_s_il_porte_des_ecritures() {
        usages.journalChoisi("OD").journalAvecEcritures("OD");

        assertThatThrownBy(() -> ouvert().exigerSuppressible(usages)).isInstanceOf(JournalUtilise.class)
                .hasMessage("Le journal OD est choisi pour une opération : changez-la avant de le supprimer");
    }

    @Test
    void un_journal_qui_porte_des_ecritures_se_desactive_mais_ne_se_supprime_pas() {
        usages.journalAvecEcritures("OD");
        Journal journal = ouvert();

        journal.redefinir("Opérations diverses", false, usages);
        assertThat(journal.actif()).isFalse();
        assertThatThrownBy(() -> journal.exigerSuppressible(usages)).isInstanceOf(JournalAvecEcritures.class)
                .hasMessage("Le journal OD porte des écritures : désactivez-le au lieu de le supprimer")
                .extracting("code").isEqualTo("JOURNAL_AVEC_ECRITURES");
    }
}
