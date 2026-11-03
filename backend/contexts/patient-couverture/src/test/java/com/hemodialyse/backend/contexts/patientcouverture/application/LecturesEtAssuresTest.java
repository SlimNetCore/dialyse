package com.hemodialyse.backend.contexts.patientcouverture.application;

import com.hemodialyse.backend.contexts.patientcouverture.application.AssuresDuCentre.Affectation;
import com.hemodialyse.backend.contexts.patientcouverture.application.AssuresDuCentre.Assure;
import com.hemodialyse.backend.contexts.patientcouverture.application.Doubles.HorlogeFixe;
import com.hemodialyse.backend.contexts.patientcouverture.application.Doubles.PatientsEnMemoire;
import com.hemodialyse.backend.contexts.patientcouverture.application.Doubles.SessionFixe;
import com.hemodialyse.backend.contexts.patientcouverture.application.Doubles.UniteImmediate;
import com.hemodialyse.backend.contexts.patientcouverture.domain.Fiches;
import com.hemodialyse.backend.contexts.patientcouverture.domain.Patient;
import com.hemodialyse.backend.contexts.patientcouverture.domain.PatientId;
import com.hemodialyse.backend.contexts.patientcouverture.domain.PatientIntrouvable;
import com.hemodialyse.backend.contexts.patientcouverture.domain.SaisieInvalide;
import com.hemodialyse.backend.contexts.patientcouverture.domain.TypeDeMouvement;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.hemodialyse.backend.contexts.patientcouverture.application.Doubles.AUJOURDHUI;
import static com.hemodialyse.backend.contexts.patientcouverture.application.Doubles.AUTRE_CENTRE;
import static com.hemodialyse.backend.contexts.patientcouverture.application.Doubles.CENTRE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Synthèses mensuelles, recherche, journal des mouvements et assurés, avec les ports de lecture en mémoire. */
class LecturesEtAssuresTest {

    private SessionFixe session;
    private final List<LigneDEffectif> effectif = new ArrayList<>();
    private CriteresDeRecherche derniersCriteres;
    private LectureDesMouvements.Filtre dernierFiltre;
    private int dernierePage;

    private final LectureDesPatients lecture = new LectureDesPatients() {
        @Override
        public PageDePatients rechercher(CentreId centre, CriteresDeRecherche criteres) {
            derniersCriteres = criteres;
            return new PageDePatients(List.of(), 0, criteres.page(), criteres.size());
        }

        @Override
        public List<LigneDEffectif> effectifDuCentre(CentreId centre) {
            return effectif;
        }
    };

    @BeforeEach
    void preparer() {
        session = new SessionFixe();
        effectif.clear();
    }

    private static LigneDEffectif ligne(String nom, String sexe, LocalDate naissance, Boolean kt, String etat, LocalDate evenement,
                                        LocalDate admission) {
        return new LigneDEffectif(UUID.randomUUID(), "C-" + nom, nom, "P", sexe, naissance, kt, etat, evenement, admission);
    }

    private ConsulterLEffectif synthese() {
        return new ConsulterLEffectif(session, lecture, new HorlogeFixe());
    }

    // ---- effectif du mois

    @Test
    void should_compter_l_effectif_du_mois_selon_la_date_de_sortie_et_l_admission() {
        LocalDate admis = LocalDate.of(2025, 1, 10);
        effectif.add(ligne("A", "M", LocalDate.of(1980, 1, 1), true, "PERMANENT", null, admis));
        effectif.add(ligne("B", "F", LocalDate.of(2010, 1, 1), false, null, null, admis));
        effectif.add(ligne("C", "MASCULIN", LocalDate.of(1950, 1, 1), null, "BIDON", null, admis));
        effectif.add(ligne("D", "?", null, false, "TRANSFERE", LocalDate.of(2026, 12, 1), admis));
        effectif.add(ligne("E", "M", LocalDate.of(1990, 1, 1), false, "TRANSFERE", LocalDate.of(2026, 3, 1), admis));
        effectif.add(ligne("F", "F", LocalDate.of(1990, 1, 1), false, "DECEDE", LocalDate.of(2026, 9, 1), admis));
        effectif.add(ligne("G", "F", LocalDate.of(1990, 1, 1), false, "PERMANENT", null, LocalDate.of(2026, 10, 5)));
        effectif.add(ligne("H", "F", LocalDate.of(1996, 1, 1), false, "GUERRI", LocalDate.of(2026, 9, 1), admis));

        ResumeDesPatients r = synthese().resume(CENTRE.valeur(), YearMonth.of(2026, 9));

        assertThat(r.totalPatients()).isEqualTo(5);
        assertThat(r.sexDistribution()).extracting(ResumeDesPatients.Tranche::code, ResumeDesPatients.Tranche::count)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("M", 2L), org.assertj.core.groups.Tuple.tuple("F", 2L),
                        org.assertj.core.groups.Tuple.tuple("AUTRE", 1L));
        assertThat(r.ageDistribution()).extracting(ResumeDesPatients.Tranche::code, ResumeDesPatients.Tranche::count)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("0_17", 1L), org.assertj.core.groups.Tuple.tuple("18_29", 0L),
                        org.assertj.core.groups.Tuple.tuple("30_44", 1L), org.assertj.core.groups.Tuple.tuple("45_59", 1L),
                        org.assertj.core.groups.Tuple.tuple("60_PLUS", 1L), org.assertj.core.groups.Tuple.tuple("INCONNU", 1L));
        assertThat(r.ktDistribution()).extracting(ResumeDesPatients.Tranche::count).containsExactly(1L, 4L);
        assertThat(r.sexDistribution().get(0).label()).isEqualTo("Masculin");
    }

    @Test
    void should_rendre_le_detail_du_mois_trie_avec_la_raison_d_inclusion() {
        LocalDate admis = LocalDate.of(2025, 1, 10);
        effectif.add(ligne("Zed", "M", null, true, "PERMANENT", null, admis));
        effectif.add(ligne("Alb", "F", null, false, "TRANSFERE", LocalDate.of(2026, 9, 20), admis));
        effectif.add(ligne("Bon", "F", null, false, "OCCASIONNEL", LocalDate.of(2026, 11, 20), admis));
        effectif.add(ligne("Sor", "F", null, false, "TRANSFERE", LocalDate.of(2026, 3, 20), admis));

        ResumeDesPatients.Detail d = synthese().detail(CENTRE.valeur(), YearMonth.of(2026, 9));

        assertThat(d.month()).isEqualTo("2026-09");
        assertThat(d.total()).isEqualTo(3);
        assertThat(d.items()).extracting(ResumeDesPatients.LigneDuDetail::nom).containsExactly("Alb", "Bon", "Zed");
        assertThat(d.items()).extracting(ResumeDesPatients.LigneDuDetail::inclusionReason)
                .containsExactly("EVENT_MONTH", "ACTIVE_PERIOD", "PERMANENT");
        assertThat(d.items().get(2).sousKt()).isTrue();
        assertThat(d.items().get(2).etatPatient()).isEqualTo("PERMANENT");
    }

    @Test
    void should_prendre_le_mois_courant_du_jour_du_centre_quand_aucun_mois_n_est_demande() {
        assertThat(synthese().mois(CENTRE.valeur(), null)).isEqualTo(YearMonth.from(AUJOURDHUI));
        assertThat(synthese().mois(CENTRE.valeur(), YearMonth.of(2026, 1))).isEqualTo(YearMonth.of(2026, 1));
        assertThat(synthese().detail(CENTRE.valeur(), null).month()).isEqualTo(YearMonth.from(AUJOURDHUI).toString());
    }

    @Test
    void should_refuser_la_synthese_d_un_autre_centre() {
        assertThatThrownBy(() -> synthese().resume(AUTRE_CENTRE.valeur(), null)).isInstanceOf(AccesRefuse.class);
        assertThatThrownBy(() -> synthese().detail(AUTRE_CENTRE.valeur(), null)).isInstanceOf(AccesRefuse.class);
    }

    // ---- recherche

    private CriteresDeRecherche criteres(int page, int size) {
        return new CriteresDeRecherche(page, size, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null);
    }

    @Test
    void should_borner_la_page_et_la_taille_de_la_recherche() {
        assertThatThrownBy(() -> criteres(-1, 20)).isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> criteres(0, 0)).isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> criteres(0, 101)).isInstanceOf(SaisieInvalide.class);
        assertThat(criteres(0, 100).size()).isEqualTo(100);
        assertThat(criteres(0, 1).size()).isEqualTo(1);
    }

    @Test
    void should_chercher_dans_le_centre_de_la_session_seulement() {
        var recherche = new RechercherLesPatients(session, lecture);

        assertThat(recherche.executer(CENTRE.valeur(), criteres(2, 10)).page()).isEqualTo(2);
        assertThat(derniersCriteres.size()).isEqualTo(10);
        assertThatThrownBy(() -> recherche.executer(AUTRE_CENTRE.valeur(), criteres(0, 10))).isInstanceOf(AccesRefuse.class);
    }

    // ---- journal des mouvements

    @Test
    void should_lister_les_mouvements_avec_pagination_bornee_et_garde_de_centre() {
        LectureDesMouvements lectureDesMouvements = (centre, filtre, page, size) -> {
            dernierFiltre = filtre;
            dernierePage = page;
            return new LectureDesMouvements.Page(List.of(), 0, page, size);
        };
        var consulter = new ConsulterLesMouvements(session, lectureDesMouvements);
        var filtre = new LectureDesMouvements.Filtre(UUID.randomUUID(), TypeDeMouvement.DECES, null, null);

        assertThat(consulter.executer(null, filtre, 3, 100).size()).isEqualTo(100);
        assertThat(dernierFiltre).isEqualTo(filtre);
        assertThat(dernierePage).isEqualTo(3);
        assertThatThrownBy(() -> consulter.executer(null, filtre, -1, 20)).isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> consulter.executer(null, filtre, 0, 0)).isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> consulter.executer(null, filtre, 0, 101)).isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> consulter.executer(AUTRE_CENTRE.valeur(), filtre, 0, 20)).isInstanceOf(AccesRefuse.class);
    }

    // ---- assurés

    private final PatientsEnMemoire patients = new PatientsEnMemoire();
    private final Doubles.AssuresEnMemoire assures = new Doubles.AssuresEnMemoire();

    private GererLesAssures gerer() {
        return new GererLesAssures(session, new HorlogeFixe(), patients, assures, new UniteImmediate(), centre -> {
        });
    }

    private PatientId patientTiers() {
        Patient p = Patient.admettre(new PatientId(UUID.randomUUID()), "PAT-1", CENTRE, Fiches.tiers("T-1", "AS-1"),
                AUJOURDHUI, Doubles.MAINTENANT, UUID::randomUUID);
        patients.enregistrer(p);
        return p.id();
    }

    private Assure assure(String numero, CentreId centre) {
        Assure a = new Assure(numero, "Dupont", "Jean", "M", null, null, null, null, null, null, centre.valeur());
        assures.assures.add(a);
        return a;
    }

    @Test
    void should_affecter_un_assure_a_la_date_du_centre_et_fermer_l_affectation_precedente() {
        PatientId patient = patientTiers();
        assure("AS-1", CENTRE);
        assure("AS-2", CENTRE);
        gerer().affecter(CENTRE.valeur(), patient.valeur(), "AS-1");

        var affecte = gerer().affecter(CENTRE.valeur(), patient.valeur(), "AS-2");

        assertThat(affecte.dateDebut()).isEqualTo(AUJOURDHUI);
        assertThat(affecte.assure().nom()).isEqualTo("Dupont");
        assertThat(assures.affectations).hasSize(2);
        assertThat(assures.affectations.get(0).dateFin()).isEqualTo(AUJOURDHUI);
        assertThat(assures.affectations.get(0).principal()).isFalse();
        assertThat(assures.affectations.get(1).actif()).isTrue();
        assertThat(gerer().historique(CENTRE.valeur(), patient.valeur())).hasSize(2);
    }

    @Test
    void should_refuser_d_affecter_un_assure_a_un_patient_assure_lui_meme_inconnu_ou_a_un_assure_inconnu() {
        Patient lui = Patient.admettre(new PatientId(UUID.randomUUID()), "PAT-2", CENTRE, Fiches.nominale(), AUJOURDHUI,
                Doubles.MAINTENANT, UUID::randomUUID);
        patients.enregistrer(lui);
        assure("AS-1", CENTRE);
        PatientId tiers = patientTiers();

        assertThatThrownBy(() -> gerer().affecter(CENTRE.valeur(), lui.id().valeur(), "AS-1")).isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> gerer().affecter(CENTRE.valeur(), UUID.randomUUID(), "AS-1")).isInstanceOf(PatientIntrouvable.class);
        assertThatThrownBy(() -> gerer().affecter(CENTRE.valeur(), tiers.valeur(), "AS-?")).isInstanceOf(SaisieInvalide.class);
    }

    @Test
    void should_refuser_d_affecter_ou_modifier_un_assure_d_un_autre_centre() {
        PatientId patient = patientTiers();
        assure("AS-X", AUTRE_CENTRE);

        assertThatThrownBy(() -> gerer().affecter(CENTRE.valeur(), patient.valeur(), "AS-X")).isInstanceOf(AccesRefuse.class);
        assertThatThrownBy(() -> gerer().modifierUnAssure(CENTRE.valeur(), "AS-X",
                new Assure("AS-X", "N", "P", "M", null, null, null, null, null, null, null))).isInstanceOf(AccesRefuse.class);
        assertThat(assures.assures.get(0).nom()).isEqualTo("Dupont");
        assertThat(assures.assures.size()).isEqualTo(1);
    }

    @Test
    void should_modifier_un_assure_du_centre_en_gardant_son_numero_et_son_centre() {
        assure("AS-1", CENTRE);

        Assure modifie = gerer().modifierUnAssure(CENTRE.valeur(), "AS-1",
                new Assure("IGNORE", "Martin", "Paul", "M", LocalDate.of(1970, 1, 1), "0555", null, null, "rue", "A+", null));

        assertThat(modifie.numeroAssurance()).isEqualTo("AS-1");
        assertThat(modifie.centre()).isEqualTo(CENTRE.valeur());
        assertThat(assures.trouver("AS-1").orElseThrow().nom()).isEqualTo("Martin");
        assertThatThrownBy(() -> gerer().modifierUnAssure(CENTRE.valeur(), "AS-?", modifie)).isInstanceOf(SaisieInvalide.class);
    }

    @Test
    void should_exiger_le_nom_pour_modifier_un_assure_en_gardant_le_remplacement_total() {
        assure("AS-1", CENTRE);
        var sansNom = new Assure("AS-1", " ", "P", null, null, null, null, null, null, null, null);
        var nul = new Assure("AS-1", null, null, null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> gerer().modifierUnAssure(CENTRE.valeur(), "AS-1", sansNom)).isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> gerer().modifierUnAssure(CENTRE.valeur(), "AS-1", nul)).isInstanceOf(SaisieInvalide.class);
        assertThat(assures.trouver("AS-1").orElseThrow().nom()).as("rien n'est écrit").isEqualTo("Dupont");
        gerer().modifierUnAssure(CENTRE.valeur(), "AS-1", new Assure("AS-1", "N", null, null, null, null, null, null, null, null, null));
        assertThat(assures.trouver("AS-1").orElseThrow().sexe()).as("remplacement total : le sexe est effacé").isNull();
        assertThat(assures.trouver("AS-1").orElseThrow().prenom()).as("remplacement total : le prénom est effacé").isNull();
    }

    @Test
    void should_prendre_le_verrou_du_centre_en_tete_de_l_affectation_et_de_la_correction_de_dates() {
        PatientId patient = patientTiers();
        assure("AS-1", CENTRE);
        List<String> ordre = new ArrayList<>();
        var gerer = new GererLesAssures(session, new HorlogeFixe(), patients, assures, new UniteImmediate(),
                centre -> ordre.add("verrou"));

        UUID affectation = gerer.affecter(CENTRE.valeur(), patient.valeur(), "AS-1").id();
        gerer.modifierLAffectation(CENTRE.valeur(), patient.valeur(), affectation, null, null);

        assertThat(ordre).containsExactly("verrou", "verrou");
    }

    @Test
    void should_corriger_les_dates_d_une_affectation_du_patient_et_du_centre_seulement() {
        PatientId patient = patientTiers();
        assure("AS-1", CENTRE);
        UUID affectation = gerer().affecter(CENTRE.valeur(), patient.valeur(), "AS-1").id();

        gerer().modifierLAffectation(CENTRE.valeur(), patient.valeur(), affectation, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 1));

        assertThat(assures.trouverAffectation(affectation).orElseThrow().dateFin()).isEqualTo(LocalDate.of(2026, 2, 1));
        assertThatThrownBy(() -> gerer().modifierLAffectation(CENTRE.valeur(), patient.valeur(), affectation,
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 2, 1))).isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> gerer().modifierLAffectation(CENTRE.valeur(), UUID.randomUUID(), affectation, null, null))
                .isInstanceOf(AccesRefuse.class);
        assertThatThrownBy(() -> gerer().modifierLAffectation(CENTRE.valeur(), patient.valeur(), UUID.randomUUID(), null, null))
                .isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> gerer().modifierLAffectation(AUTRE_CENTRE.valeur(), patient.valeur(), affectation, null, null))
                .isInstanceOf(AccesRefuse.class);
    }

    @Test
    void should_lister_les_assures_du_centre_de_la_session_seulement() {
        assure("AS-1", CENTRE);
        assure("AS-2", AUTRE_CENTRE);

        assertThat(gerer().lister(CENTRE.valeur(), null)).extracting(Assure::numeroAssurance).containsExactly("AS-1");
        assertThatThrownBy(() -> gerer().lister(AUTRE_CENTRE.valeur(), null)).isInstanceOf(AccesRefuse.class);
    }
}
