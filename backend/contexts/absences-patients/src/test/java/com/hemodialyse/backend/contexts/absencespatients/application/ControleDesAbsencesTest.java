package com.hemodialyse.backend.contexts.absencespatients.application;

import com.hemodialyse.backend.contexts.absencespatients.application.ControlerLesAbsences.ResultatDuCentre;
import com.hemodialyse.backend.contexts.absencespatients.domain.AbsencePatient;
import com.hemodialyse.backend.contexts.absencespatients.domain.ForfaitApplicable;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.PatientId;
import com.hemodialyse.backend.contexts.absencespatients.domain.RegleDAbsenceViolee;
import com.hemodialyse.backend.contexts.absencespatients.domain.SourceAbsence;
import com.hemodialyse.backend.contexts.absencespatients.domain.StatutAbsence;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static com.hemodialyse.backend.contexts.absencespatients.application.MondeDeLaDetection.AMRANI;
import static com.hemodialyse.backend.contexts.absencespatients.application.MondeDeLaDetection.AUJOURDHUI;
import static com.hemodialyse.backend.contexts.absencespatients.application.MondeDeLaDetection.AUTRE_CENTRE;
import static com.hemodialyse.backend.contexts.absencespatients.application.MondeDeLaDetection.BELKACEM;
import static com.hemodialyse.backend.contexts.absencespatients.application.MondeDeLaDetection.CENTRE;
import static com.hemodialyse.backend.contexts.absencespatients.application.MondeDeLaDetection.CHERIF;
import static com.hemodialyse.backend.contexts.absencespatients.application.MondeDeLaDetection.FORFAIT;
import static com.hemodialyse.backend.contexts.absencespatients.application.MondeDeLaDetection.HIER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Le contrôle de nuit, le rattrapage au démarrage et le rattrapage manuel de la détection (matrice A12 à A15), avec les doubles
 * en mémoire des ports.
 */
class ControleDesAbsencesTest {

    private final MondeDeLaDetection monde = new MondeDeLaDetection();

    private ResultatDuCentre nuit() {
        return monde.controler.controlerLaNuit().get(0);
    }

    // --- détection ---

    @Test
    void la_nuit_detecte_les_sept_derniers_jours_termines_sans_aujourd_hui() {
        for (int i = 0; i <= 8; i++) {
            monde.attendre(CENTRE, AUJOURDHUI.minusDays(i), AMRANI);
        }

        ResultatDuCentre resultat = nuit();

        assertThat(resultat.bilan().creees()).isEqualTo(7);
        assertThat(monde.absencesDe(CENTRE)).extracting(AbsencePatient::dateDeSeance)
                .containsExactlyInAnyOrderElementsOf(IntStream.rangeClosed(1, 7).mapToObj(AUJOURDHUI::minusDays).toList());
    }

    @Test
    void une_absence_detectee_est_a_qualifier_automatique_et_valorisee_au_forfait_du_jour() {
        monde.attendre(CENTRE, HIER, AMRANI, BELKACEM);

        nuit();

        AbsencePatient avecForfait = monde.absencesDe(CENTRE).stream().filter(a -> a.patient().equals(AMRANI)).findFirst().orElseThrow();
        assertThat(avecForfait.statut()).isEqualTo(StatutAbsence.A_QUALIFIER);
        assertThat(avecForfait.source()).isEqualTo(SourceAbsence.AUTOMATIQUE);
        assertThat(avecForfait.declareePar()).isNull();
        assertThat(avecForfait.valorisation().montantHt()).isEqualByComparingTo("10000.00");
        assertThat(avecForfait.valorisation().montantTtc()).isEqualByComparingTo("11900.00");
        AbsencePatient sansForfait = monde.absencesDe(CENTRE).stream().filter(a -> a.patient().equals(BELKACEM)).findFirst().orElseThrow();
        assertThat(sansForfait.valorisation().montantHt()).isEqualByComparingTo("0");
        assertThat(sansForfait.valorisation().tauxDeTva()).isEqualByComparingTo("19");
    }

    @Test
    void un_second_passage_ne_cree_rien_et_ne_touche_pas_aux_absences_existantes() {
        monde.attendre(CENTRE, HIER, AMRANI);
        nuit();
        AbsencePatient avant = monde.absencesDe(CENTRE).get(0);

        ResultatDuCentre second = nuit();

        assertThat(second.bilan().creees()).isZero();
        assertThat(monde.absencesDe(CENTRE)).hasSize(1);
        assertThat(monde.absencesDe(CENTRE).get(0).declareeLe()).isEqualTo(avant.declareeLe());
    }

    @Test
    void une_absence_annulee_ou_declaree_du_meme_jour_n_est_jamais_recreee() {
        monde.attendre(CENTRE, HIER, AMRANI, BELKACEM);
        monde.poser(1, CENTRE, AMRANI, HIER, SourceAbsence.AUTOMATIQUE, StatutAbsence.ANNULEE);
        monde.poser(2, CENTRE, BELKACEM, HIER, SourceAbsence.DECLAREE, StatutAbsence.JUSTIFIEE);

        assertThat(nuit().bilan().creees()).isZero();
        assertThat(monde.absencesDe(CENTRE)).extracting(AbsencePatient::statut)
                .containsExactlyInAnyOrder(StatutAbsence.ANNULEE, StatutAbsence.JUSTIFIEE);
    }

    @Test
    void la_periode_facturee_du_patient_n_est_pas_detectee_mais_celle_d_un_autre_patient_l_est() {
        monde.attendre(CENTRE, HIER, AMRANI, BELKACEM);
        monde.facturer(AMRANI, HIER);

        nuit();

        assertThat(monde.absencesDe(CENTRE)).extracting(AbsencePatient::patient).containsExactly(BELKACEM);
    }

    @Test
    void un_mois_d_absences_clos_est_saute_et_journalise_dans_le_bilan() {
        monde.instant = Instant.parse("2026-11-03T10:00:00Z");
        LocalDate dernierJourDOctobre = LocalDate.of(2026, 10, 31);
        LocalDate premierJourDeNovembre = LocalDate.of(2026, 11, 1);
        monde.attendre(CENTRE, dernierJourDOctobre, AMRANI);
        monde.attendre(CENTRE, premierJourDeNovembre, AMRANI);
        monde.moisClos.add(YearMonth.of(2026, 10));

        ResultatDuCentre resultat = nuit();

        assertThat(monde.absencesDe(CENTRE)).extracting(AbsencePatient::dateDeSeance).containsExactly(premierJourDeNovembre);
        assertThat(resultat.bilan().joursIgnores()).contains(dernierJourDOctobre).doesNotContain(premierJourDeNovembre);
    }

    @Test
    void une_cloture_concurrente_entre_la_lecture_et_l_ecriture_empeche_la_creation_d_une_absence_dans_le_mois_clos() {
        monde.attendre(CENTRE, HIER, AMRANI);
        monde.moisClosEntreTemps.add(YearMonth.from(HIER));

        ResultatDuCentre resultat = nuit();

        assertThat(monde.absencesDe(CENTRE)).isEmpty();
        assertThat(resultat.bilan().creees()).isZero();
        assertThat(resultat.bilan().echecs()).isEmpty();
    }

    @Test
    void une_cloture_concurrente_entre_la_lecture_et_l_ecriture_laisse_l_absence_a_reconcilier_dans_le_mois_clos() {
        AbsencePatient absence = monde.poser(1, CENTRE, AMRANI, HIER, SourceAbsence.AUTOMATIQUE, StatutAbsence.A_QUALIFIER);
        monde.seances.poserLaSeance(CENTRE, AMRANI, HIER, "VALIDEE");
        monde.moisClosEntreTemps.add(YearMonth.from(HIER));

        ResultatDuCentre resultat = nuit();

        assertThat(resultat.bilan().annulees()).isZero();
        assertThat(monde.absences.lire(absence.id()).orElseThrow().statut()).isEqualTo(StatutAbsence.A_QUALIFIER);
    }

    @Test
    void un_verrou_de_mois_indisponible_est_un_echec_compte_et_pas_une_declaration_concurrente() {
        monde.attendre(CENTRE, HIER, AMRANI);
        monde.moisVerrouIndisponible.add(YearMonth.from(HIER));

        ResultatDuCentre resultat = nuit();

        assertThat(monde.absencesDe(CENTRE)).isEmpty();
        assertThat(resultat.bilan().echecs()).hasSize(1);
        assertThat(resultat.bilan().echecs().getFirst().cause()).isEqualTo("écriture concurrente");
    }

    // --- isolement des incidents ---

    @Test
    void une_absence_en_incident_n_arrete_que_cette_absence_et_le_centre_est_quand_meme_notifie() {
        monde.forfaits.put(AMRANI, new ForfaitApplicable(FORFAIT, "Forfait faux", new BigDecimal("-1.00")));
        monde.attendre(CENTRE, HIER, AMRANI, BELKACEM, CHERIF);
        monde.attendre(CENTRE, HIER.minusDays(1), AMRANI, BELKACEM);

        ResultatDuCentre resultat = nuit();

        assertThat(resultat.incident()).isNull();
        assertThat(resultat.bilan().creees()).isEqualTo(3);
        assertThat(resultat.bilan().echecs()).hasSize(2)
                .allSatisfy(e -> {
                    assertThat(e.patient()).isEqualTo(AMRANI);
                    assertThat(e.operation()).isEqualTo("détection");
                    assertThat(e.cause()).contains("IllegalArgumentException");
                });
        assertThat(monde.absencesDe(CENTRE)).extracting(AbsencePatient::patient).doesNotContain(AMRANI);
        assertThat(resultat.notifie()).isTrue();
        assertThat(monde.notifications).hasSize(1);
    }

    @Test
    void un_centre_en_incident_n_arrete_pas_les_autres_et_n_est_pas_notifie() {
        monde.centres.add(AUTRE_CENTRE);
        monde.centresEnIncident.add(CENTRE);
        monde.attendre(AUTRE_CENTRE, HIER, AMRANI);

        List<ResultatDuCentre> resultats = monde.controler.controlerLaNuit();

        assertThat(resultats.get(0).incident()).contains("base indisponible");
        assertThat(resultats.get(0).notifie()).isFalse();
        assertThat(resultats.get(1).incident()).isNull();
        assertThat(monde.absencesDe(AUTRE_CENTRE)).hasSize(1);
        assertThat(monde.notifications).containsExactly(AUTRE_CENTRE.valeur() + "/1/0");
    }

    @Test
    void chaque_absence_creee_est_sa_propre_transaction() {
        monde.attendre(CENTRE, HIER, AMRANI, BELKACEM, CHERIF);

        nuit();

        assertThat(monde.unite.executions()).isGreaterThanOrEqualTo(3);
    }

    // --- réconciliation ---

    @Test
    void la_reconciliation_annule_l_absence_detectee_dont_la_seance_est_realisee_sans_auteur() {
        AbsencePatient detectee = monde.poser(1, CENTRE, AMRANI, HIER, SourceAbsence.AUTOMATIQUE, StatutAbsence.A_QUALIFIER);
        monde.seances.poserLaSeance(CENTRE, AMRANI, HIER, "VALIDEE");

        ResultatDuCentre resultat = nuit();

        assertThat(resultat.bilan().annulees()).isEqualTo(1);
        AbsencePatient relue = monde.absences.lire(detectee.id()).orElseThrow();
        assertThat(relue.statut()).isEqualTo(StatutAbsence.ANNULEE);
        assertThat(relue.commentaire()).isEqualTo("Séance réalisée à cette date");
        assertThat(relue.modifieePar()).isNull();
        assertThat(relue.modifieeLe()).isEqualTo(MondeDeLaDetection.MIDI);
    }

    @Test
    void la_reconciliation_laisse_les_absences_declarees_qualifiees_ou_facturees() {
        AbsencePatient declaree = monde.poser(1, CENTRE, AMRANI, HIER, SourceAbsence.DECLAREE, StatutAbsence.A_QUALIFIER);
        AbsencePatient qualifiee = monde.poser(2, CENTRE, BELKACEM, HIER, SourceAbsence.AUTOMATIQUE, StatutAbsence.JUSTIFIEE);
        AbsencePatient facturee = monde.poser(3, CENTRE, CHERIF, HIER, SourceAbsence.AUTOMATIQUE, StatutAbsence.A_QUALIFIER);
        monde.facturer(CHERIF, HIER);
        for (PatientId p : List.of(AMRANI, BELKACEM, CHERIF)) {
            monde.seances.poserLaSeance(CENTRE, p, HIER, "SIGNEE");
        }

        assertThat(nuit().bilan().annulees()).isZero();

        assertThat(monde.absences.lire(declaree.id()).orElseThrow().statut()).isEqualTo(StatutAbsence.A_QUALIFIER);
        assertThat(monde.absences.lire(qualifiee.id()).orElseThrow().statut()).isEqualTo(StatutAbsence.JUSTIFIEE);
        assertThat(monde.absences.lire(facturee.id()).orElseThrow().statut()).isEqualTo(StatutAbsence.A_QUALIFIER);
    }

    @Test
    void une_seance_non_realisee_ne_reconcilie_pas() {
        monde.poser(1, CENTRE, AMRANI, HIER, SourceAbsence.AUTOMATIQUE, StatutAbsence.A_QUALIFIER);
        monde.seances.poserLaSeance(CENTRE, AMRANI, HIER, "CREE");

        assertThat(nuit().bilan().annulees()).isZero();
    }

    @Test
    void la_reconciliation_traite_toutes_les_absences_en_un_seul_passage_au_dela_d_une_page() {
        int total = 2 * ReconciliationAbsences.TAILLE_DE_PAGE + 50;
        for (int i = 1; i <= total; i++) {
            PatientId patient = new PatientId(new UUID(40, i));
            monde.poser(i, CENTRE, patient, HIER, SourceAbsence.AUTOMATIQUE, StatutAbsence.A_QUALIFIER);
            monde.seances.poserLaSeance(CENTRE, patient, HIER, "VALIDEE");
        }

        assertThat(nuit().bilan().annulees()).isEqualTo(total);
        assertThat(monde.absencesDe(CENTRE)).allMatch(a -> a.statut() == StatutAbsence.ANNULEE);
    }

    @Test
    void une_reconciliation_en_incident_n_arrete_que_cette_absence() {
        AbsencePatient fautive = monde.poser(1, CENTRE, AMRANI, HIER, SourceAbsence.AUTOMATIQUE, StatutAbsence.A_QUALIFIER);
        monde.poser(2, CENTRE, BELKACEM, HIER, SourceAbsence.AUTOMATIQUE, StatutAbsence.A_QUALIFIER);
        monde.seances.poserLaSeance(CENTRE, AMRANI, HIER, "VALIDEE");
        monde.seances.poserLaSeance(CENTRE, BELKACEM, HIER, "VALIDEE");
        monde.enregistrementsEnEchec.add(fautive.id());

        ResultatDuCentre resultat = nuit();

        assertThat(resultat.bilan().annulees()).isEqualTo(1);
        assertThat(resultat.bilan().echecs()).hasSize(1);
        assertThat(resultat.bilan().echecs().get(0).operation()).isEqualTo("réconciliation");
        assertThat(monde.absences.lire(fautive.id()).orElseThrow().statut()).isEqualTo(StatutAbsence.A_QUALIFIER);
    }

    @Test
    void la_reconciliation_saute_un_mois_d_absences_clos() {
        monde.instant = Instant.parse("2026-11-03T10:00:00Z");
        LocalDate octobre = LocalDate.of(2026, 10, 31);
        AbsencePatient absence = monde.poser(1, CENTRE, AMRANI, octobre, SourceAbsence.AUTOMATIQUE, StatutAbsence.A_QUALIFIER);
        monde.seances.poserLaSeance(CENTRE, AMRANI, octobre, "VALIDEE");
        monde.moisClos.add(YearMonth.of(2026, 10));

        ResultatDuCentre resultat = nuit();

        assertThat(resultat.bilan().annulees()).isZero();
        assertThat(resultat.bilan().joursIgnores()).contains(octobre);
        assertThat(monde.absences.lire(absence.id()).orElseThrow().statut()).isEqualTo(StatutAbsence.A_QUALIFIER);
    }

    @Test
    void la_reconciliation_passe_avant_la_detection_et_une_seance_realisee_ne_cree_pas_d_absence() {
        monde.poser(1, CENTRE, AMRANI, HIER, SourceAbsence.AUTOMATIQUE, StatutAbsence.A_QUALIFIER);
        monde.seances.poserLaSeance(CENTRE, AMRANI, HIER, "VALIDEE");
        monde.attendre(CENTRE, HIER, AMRANI);

        ResultatDuCentre resultat = nuit();

        assertThat(resultat.bilan().annulees()).isEqualTo(1);
        assertThat(resultat.bilan().creees()).isZero();
    }

    // --- notification ---

    @Test
    void la_nuit_notifie_les_absences_a_qualifier_de_la_fenetre_dont_celles_en_retard() {
        monde.attendre(CENTRE, AUJOURDHUI.minusDays(1), AMRANI);
        monde.attendre(CENTRE, AUJOURDHUI.minusDays(3), AMRANI);
        monde.attendre(CENTRE, AUJOURDHUI.minusDays(4), AMRANI);
        monde.attendre(CENTRE, AUJOURDHUI.minusDays(7), AMRANI);

        ResultatDuCentre resultat = nuit();

        assertThat(resultat.notifie()).isTrue();
        assertThat(monde.notifications).containsExactly(CENTRE.valeur() + "/4/2");
    }

    @Test
    void les_absences_a_qualifier_plus_anciennes_que_la_fenetre_sont_comptees_pas_les_autres_statuts() {
        monde.poser(1, CENTRE, AMRANI, AUJOURDHUI.minusDays(20), SourceAbsence.AUTOMATIQUE, StatutAbsence.A_QUALIFIER);
        monde.poser(2, CENTRE, BELKACEM, AUJOURDHUI.minusDays(5), SourceAbsence.DECLAREE, StatutAbsence.A_QUALIFIER);
        monde.poser(3, CENTRE, CHERIF, AUJOURDHUI.minusDays(5), SourceAbsence.AUTOMATIQUE, StatutAbsence.JUSTIFIEE);

        nuit();

        assertThat(monde.notifications).containsExactly(CENTRE.valeur() + "/2/2");
    }

    @Test
    void sans_absence_a_qualifier_aucune_notification() {
        assertThat(nuit().notifie()).isFalse();
        assertThat(monde.notifications).isEmpty();
    }

    @Test
    void une_seule_notification_par_centre_et_par_jour_du_centre() {
        monde.attendre(CENTRE, HIER, AMRANI);

        nuit();
        ResultatDuCentre seconde = nuit();

        assertThat(monde.notifications).hasSize(1);
        assertThat(seconde.notifie()).isFalse();
    }

    @Test
    void le_lendemain_une_nouvelle_notification_est_envoyee() {
        monde.attendre(CENTRE, HIER, AMRANI);
        nuit();
        monde.instant = monde.instant.plusSeconds(24 * 3600);

        nuit();

        assertThat(monde.notifications).hasSize(2);
    }

    @Test
    void les_absences_d_un_mois_clos_ne_sont_pas_comptees_dans_la_notification() {
        monde.instant = Instant.parse("2026-11-03T10:00:00Z");
        monde.poser(1, CENTRE, AMRANI, LocalDate.of(2026, 10, 30), SourceAbsence.AUTOMATIQUE, StatutAbsence.A_QUALIFIER);
        monde.poser(2, CENTRE, BELKACEM, LocalDate.of(2026, 11, 1), SourceAbsence.AUTOMATIQUE, StatutAbsence.A_QUALIFIER);
        monde.moisClos.add(YearMonth.of(2026, 10));

        nuit();

        assertThat(monde.notifications).containsExactly(CENTRE.valeur() + "/1/0");
    }

    @Test
    void chaque_centre_a_sa_propre_notification() {
        monde.centres.add(AUTRE_CENTRE);
        monde.attendre(CENTRE, HIER, AMRANI, BELKACEM);
        monde.attendre(AUTRE_CENTRE, HIER, AMRANI);

        monde.controler.controlerLaNuit();

        assertThat(monde.notifications).contains(CENTRE.valeur() + "/2/0");
    }

    @Test
    void le_rattrapage_au_demarrage_couvre_un_mois_sans_notifier() {
        monde.attendre(CENTRE, AUJOURDHUI.minusDays(31), AMRANI);
        monde.attendre(CENTRE, AUJOURDHUI.minusDays(32), AMRANI);
        monde.attendre(CENTRE, AUJOURDHUI.minusDays(10), BELKACEM);

        ResultatDuCentre resultat = monde.controler.rattraperAuDemarrage().get(0);

        assertThat(resultat.bilan().creees()).isEqualTo(2);
        assertThat(resultat.notifie()).isFalse();
        assertThat(monde.notifications).isEmpty();
    }

    // --- fuseau du centre ---

    @Test
    void la_fenetre_suit_le_fuseau_du_centre() {
        monde.instant = Instant.parse("2026-10-21T23:30:00Z");
        monde.fuseaux.put(CENTRE, ZoneId.of("Africa/Algiers"));
        monde.centres.add(AUTRE_CENTRE);
        monde.attendre(CENTRE, LocalDate.of(2026, 10, 21), AMRANI);
        monde.attendre(AUTRE_CENTRE, LocalDate.of(2026, 10, 21), AMRANI);

        monde.controler.controlerLaNuit();

        assertThat(monde.absencesDe(CENTRE)).as("à Alger, il est déjà le 22 : le 21 est terminé").hasSize(1);
        assertThat(monde.absencesDe(AUTRE_CENTRE)).as("en UTC, le 21 n'est pas terminé").isEmpty();
    }

    // --- rattrapage manuel ---

    @Test
    void le_rattrapage_manuel_detecte_et_reconcilie_la_periode_sans_notifier() {
        monde.attendre(CENTRE, AUJOURDHUI.minusDays(100), AMRANI);
        monde.poser(1, CENTRE, BELKACEM, AUJOURDHUI.minusDays(99), SourceAbsence.AUTOMATIQUE, StatutAbsence.A_QUALIFIER);
        monde.seances.poserLaSeance(CENTRE, BELKACEM, AUJOURDHUI.minusDays(99), "FACTUREE");

        var rattrapage = monde.detecterUnePeriode.executer(CENTRE, AUJOURDHUI.minusDays(120), AUJOURDHUI.minusDays(90));

        assertThat(rattrapage.bilan().creees()).isEqualTo(1);
        assertThat(rattrapage.bilan().annulees()).isEqualTo(1);
        assertThat(rattrapage.periode().finRamenee()).isFalse();
        assertThat(monde.notifications).isEmpty();
    }

    @Test
    void le_rattrapage_manuel_signale_la_fin_ramenee_a_hier() {
        var rattrapage = monde.detecterUnePeriode.executer(CENTRE, AUJOURDHUI.minusDays(3), AUJOURDHUI.plusDays(5));

        assertThat(rattrapage.periode().au()).isEqualTo(HIER);
        assertThat(rattrapage.periode().finRamenee()).isTrue();
    }

    @Test
    void le_rattrapage_manuel_refuse_une_periode_invalide_ou_trop_longue() {
        assertThatThrownBy(() -> monde.detecterUnePeriode.executer(CENTRE, AUJOURDHUI, AUJOURDHUI))
                .isInstanceOf(RegleDAbsenceViolee.class);
        assertThatThrownBy(() -> monde.detecterUnePeriode.executer(CENTRE, HIER.minusDays(366), HIER))
                .isInstanceOf(RegleDAbsenceViolee.class);
        assertThat(monde.absences.nombre()).isZero();
    }

    @Test
    void le_rattrapage_manuel_rapporte_les_absences_en_incident() {
        monde.forfaits.put(AMRANI, new ForfaitApplicable(FORFAIT, "Forfait faux", new BigDecimal("-1.00")));
        monde.attendre(CENTRE, HIER, AMRANI, BELKACEM);

        var rattrapage = monde.detecterUnePeriode.executer(CENTRE, HIER, HIER);

        assertThat(rattrapage.bilan().creees()).isEqualTo(1);
        assertThat(rattrapage.bilan().echecs()).hasSize(1);
    }
}
