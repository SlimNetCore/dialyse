package com.hemodialyse.backend.contexts.comptabilite.application.journal;

import com.hemodialyse.backend.contexts.comptabilite.application.PageDe;
import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;
import com.hemodialyse.backend.contexts.comptabilite.domain.journal.CodeJournal;
import com.hemodialyse.backend.contexts.comptabilite.domain.journal.JournalAvecEcritures;
import com.hemodialyse.backend.contexts.comptabilite.domain.journal.JournalIntrouvable;
import com.hemodialyse.backend.contexts.comptabilite.domain.journal.JournalUtilise;
import com.hemodialyse.backend.contexts.comptabilite.doubles.JournauxEnMemoire;
import com.hemodialyse.backend.contexts.comptabilite.doubles.UniteDeTravailDirecte;
import com.hemodialyse.backend.contexts.comptabilite.doubles.UsagesEnMemoire;
import com.hemodialyse.backend.contexts.comptabilite.publishedlanguage.JournauxDuCentre.JournalPublie;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JournauxCasDUsageTest {

    private static final CentreId CENTRE = new CentreId(UUID.randomUUID());

    private final JournauxEnMemoire journaux = new JournauxEnMemoire();
    private final UsagesEnMemoire usages = new UsagesEnMemoire();
    private final UniteDeTravailDirecte unite = new UniteDeTravailDirecte();
    private final JournauxDuCentreVus vus = new JournauxDuCentreVus(journaux);
    private final ListerLesJournaux lister = new ListerLesJournaux(vus, unite);
    private final EnregistrerUnJournal enregistrer = new EnregistrerUnJournal(journaux, vus, usages, unite);
    private final SupprimerUnJournal supprimer = new SupprimerUnJournal(journaux, vus, usages, unite);
    private final ConsulterLesJournaux consulter = new ConsulterLesJournaux(vus);

    @Test
    void sans_journal_enregistre_les_cinq_journaux_par_defaut_s_affichent_dans_l_ordre_de_declaration() {
        PageDe<JournalVue> page = lister.executer(CENTRE, 0, 20);

        assertThat(page.items()).extracting(JournalVue::code).containsExactly("VE", "BQ", "CA", "AC", "ST");
        assertThat(page.total()).isEqualTo(5);
        assertThat(journaux.duCentre(CENTRE)).isEmpty();
    }

    @Test
    void la_page_est_decoupee_en_memoire_avec_une_taille_de_1_a_100() {
        assertThat(lister.executer(CENTRE, 1, 2).items()).extracting(JournalVue::code).containsExactly("CA", "AC");
        assertThat(lister.executer(CENTRE, 0, 0).items()).hasSize(1);
        assertThat(lister.executer(CENTRE, 0, 1000).size()).isEqualTo(100);
        assertThat(lister.executer(CENTRE, -1, 3).page()).isZero();
        PageDe<JournalVue> horsLimites = lister.executer(CENTRE, 8, 20);
        assertThat(horsLimites.items()).isEmpty();
        assertThat(horsLimites.total()).isEqualTo(5);
    }

    @Test
    void la_premiere_modification_enregistre_les_journaux_par_defaut_puis_le_journal() {
        JournalVue enregistre = enregistrer.executer(CENTRE, "od", "Opérations diverses", true);

        assertThat(enregistre).isEqualTo(new JournalVue("OD", "Opérations diverses", true));
        assertThat(journaux.duCentre(CENTRE)).extracting(j -> j.code().valeur()).containsExactly("AC", "BQ", "CA", "OD", "ST", "VE");
    }

    @Test
    void modifier_un_journal_existant_remplace_son_libelle_et_son_etat() {
        enregistrer.executer(CENTRE, "OD", "Opérations diverses", true);

        JournalVue modifie = enregistrer.executer(CENTRE, " od ", " Divers ", false);

        assertThat(modifie).isEqualTo(new JournalVue("OD", "Divers", false));
        assertThat(journaux.duCentre(CENTRE)).hasSize(6);
    }

    @Test
    void desactiver_un_journal_choisi_pour_une_operation_est_refuse() {
        usages.journalChoisi("VE");

        assertThatThrownBy(() -> enregistrer.executer(CENTRE, "VE", "Ventes", false)).isInstanceOf(JournalUtilise.class);
    }

    @Test
    void desactiver_un_journal_avec_ecritures_est_permis() {
        usages.journalAvecEcritures("AC");

        assertThat(enregistrer.executer(CENTRE, "AC", "Achats", false).actif()).isFalse();
    }

    @Test
    void un_code_mal_forme_est_refuse_sans_rien_enregistrer() {
        assertThatThrownBy(() -> enregistrer.executer(CENTRE, "V-E", "Ventes", true)).isInstanceOf(IllegalArgumentException.class);
        assertThat(journaux.duCentre(CENTRE)).isEmpty();
    }

    @Test
    void supprimer_un_journal_libre_le_retire() {
        enregistrer.executer(CENTRE, "OD", "Opérations diverses", true);

        supprimer.executer(CENTRE, "od");

        assertThat(journaux.trouver(CENTRE, CodeJournal.de("OD"))).isEmpty();
    }

    @Test
    void supprimer_un_journal_inconnu_est_refuse() {
        assertThatThrownBy(() -> supprimer.executer(CENTRE, "OD")).isInstanceOf(JournalIntrouvable.class)
                .hasMessage("Journal OD introuvable");
    }

    @Test
    void supprimer_un_journal_utilise_est_prioritaire_sur_les_ecritures() {
        usages.journalChoisi("VE").journalAvecEcritures("VE");

        assertThatThrownBy(() -> supprimer.executer(CENTRE, "VE")).isInstanceOf(JournalUtilise.class);
    }

    @Test
    void supprimer_un_journal_avec_ecritures_est_refuse() {
        usages.journalAvecEcritures("AC");

        assertThatThrownBy(() -> supprimer.executer(CENTRE, "AC")).isInstanceOf(JournalAvecEcritures.class);
    }

    @Test
    void supprimer_le_dernier_journal_fait_reapparaitre_les_cinq_journaux_par_defaut_KB_CPT_07() {
        journaux.enregistrer(com.hemodialyse.backend.contexts.comptabilite.domain.journal.Journal.reconstitue(CENTRE,
                CodeJournal.de("OD"), "Seul", true));

        // le seul journal enregistré se supprime : la lecture retombe sur les journaux par défaut, sans les enregistrer
        journaux.supprimer(CENTRE, CodeJournal.de("OD"));

        assertThat(journaux.duCentre(CENTRE)).isEmpty();
        assertThat(lister.executer(CENTRE, 0, 20).items()).extracting(JournalVue::code).containsExactly("VE", "BQ", "CA", "AC", "ST");
    }

    @Test
    void le_langage_publie_rend_les_journaux_effectifs_du_centre() {
        assertThat(consulter.journaux(CENTRE.valeur())).extracting(JournalPublie::code).containsExactly("VE", "BQ", "CA", "AC", "ST");
        enregistrer.executer(CENTRE, "OD", "Opérations diverses", false);

        assertThat(consulter.journaux(CENTRE.valeur())).contains(new JournalPublie("OD", "Opérations diverses", false));
    }
}
