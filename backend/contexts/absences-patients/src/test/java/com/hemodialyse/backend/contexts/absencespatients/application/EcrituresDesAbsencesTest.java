package com.hemodialyse.backend.contexts.absencespatients.application;

import com.hemodialyse.backend.contexts.absencespatients.domain.AbsenceIntrouvable;
import com.hemodialyse.backend.contexts.absencespatients.domain.AbsenceNonModifiable;
import com.hemodialyse.backend.contexts.absencespatients.domain.AbsencePatient;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.AbsenceId;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.PatientId;
import com.hemodialyse.backend.contexts.absencespatients.domain.MotifAbsence;
import com.hemodialyse.backend.contexts.absencespatients.domain.RegleDAbsenceViolee;
import com.hemodialyse.backend.contexts.absencespatients.domain.SaisieInvalide;
import com.hemodialyse.backend.contexts.absencespatients.domain.SourceAbsence;
import com.hemodialyse.backend.contexts.absencespatients.domain.StatutAbsence;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static com.hemodialyse.backend.contexts.absencespatients.application.MondeDesEcritures.AMRANI;
import static com.hemodialyse.backend.contexts.absencespatients.application.MondeDesEcritures.AUJOURDHUI;
import static com.hemodialyse.backend.contexts.absencespatients.application.MondeDesEcritures.AUTRE_CENTRE;
import static com.hemodialyse.backend.contexts.absencespatients.application.MondeDesEcritures.AVEC_DROIT;
import static com.hemodialyse.backend.contexts.absencespatients.application.MondeDesEcritures.BELKACEM;
import static com.hemodialyse.backend.contexts.absencespatients.application.MondeDesEcritures.CENTRE;
import static com.hemodialyse.backend.contexts.absencespatients.application.MondeDesEcritures.MAINTENANT;
import static com.hemodialyse.backend.contexts.absencespatients.application.MondeDesEcritures.SANS_DROIT;
import static com.hemodialyse.backend.contexts.absencespatients.application.MondeDesEcritures.SECRETAIRE;
import static com.hemodialyse.backend.contexts.absencespatients.application.MondeDesEcritures.SEANCE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cas d'usage d'écriture des absences (déclarer, qualifier, rattraper, annuler, retirer l'absence d'une exclusion), avec les
 * doubles en mémoire des ports : une transaction par absence, ordre des contrôles, rien d'écrit ni de signalé sur un refus.
 */
class EcrituresDesAbsencesTest {

    private final MondeDesEcritures monde = new MondeDesEcritures();

    private static void assertRefus(Runnable action, String code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(RegleDAbsenceViolee.class, e -> assertThat(e.code()).isEqualTo(code));
    }

    // --- déclarer

    @Test
    void declarer_fige_la_valeur_du_forfait_et_du_taux_trace_le_declarant_et_signale_la_saisie() {
        LigneDuSuivi ligne = monde.declarer.executer(CENTRE, monde.commande(AMRANI, SEANCE, null, null), SANS_DROIT);

        AbsencePatient lue = monde.absences.lire(ligne.absence().id()).orElseThrow();
        assertThat(lue.statut()).isEqualTo(StatutAbsence.A_QUALIFIER);
        assertThat(lue.source()).isEqualTo(SourceAbsence.DECLAREE);
        assertThat(lue.declareePar()).isEqualTo(SECRETAIRE);
        assertThat(lue.declareeLe()).isEqualTo(MAINTENANT);
        assertThat(lue.valorisation().montantHt()).isEqualTo(new BigDecimal("10000.00"));
        assertThat(lue.valorisation().montantTtc()).isEqualTo(new BigDecimal("11900.00"));
        assertThat(lue.valorisation().tauxDeTva()).isEqualTo(new BigDecimal("19.00"));
        assertThat(lue.valorisation().libelleDuForfait()).isEqualTo("Forfait HD");
        assertThat(monde.signalements).containsExactly(CENTRE.valeur() + "/" + AMRANI.valeur() + "/" + SEANCE);
        assertThat(ligne.nomDuPatient()).isNull();
        assertThat(ligne.enRetard()).isTrue();
        assertThat(ligne.absence().montantTtc()).isEqualByComparingTo("11900.00");
    }

    @Test
    void declarer_avec_motif_qualifie_d_emblee_et_conserve_le_commentaire_meme_sans_motif() {
        AbsencePatient avecMotif = monde.declaree(AMRANI, SEANCE, MotifAbsence.NON_JUSTIFIEE);
        monde.declarer.executer(CENTRE, monde.commande(BELKACEM, SEANCE, null, " visite à domicile "), SANS_DROIT);

        assertThat(avecMotif.statut()).isEqualTo(StatutAbsence.NON_JUSTIFIEE);
        assertThat(avecMotif.qualifieePar()).isEqualTo(SECRETAIRE);
        assertThat(monde.absences.nombre()).isEqualTo(2);
    }

    @Test
    void declarer_sans_forfait_valorise_a_zero_et_une_regle_exoneree_donne_un_taux_nul() {
        monde.forfaits.clear();
        monde.taux = BigDecimal.ZERO;

        AbsencePatient absence = monde.declaree(AMRANI, SEANCE, null);

        assertThat(absence.valorisation().montantHt()).isEqualTo(new BigDecimal("0.00"));
        assertThat(absence.valorisation().montantTtc()).isEqualTo(new BigDecimal("0.00"));
        assertThat(absence.valorisation().tauxDeTva().toPlainString()).isEqualTo("0.00");
        assertThat(absence.valorisation().forfait()).isNull();
    }

    @Test
    void declarer_refuse_un_patient_inconnu_ou_d_un_autre_centre() {
        assertRefus(() -> monde.declarer.executer(CENTRE, monde.commande(new PatientId(UUID.randomUUID()), SEANCE, null, null),
                SANS_DROIT), "PATIENT_INTROUVABLE");
        assertRefus(() -> monde.declarer.executer(AUTRE_CENTRE, monde.commande(AMRANI, SEANCE, null, null), SANS_DROIT),
                "PATIENT_INTROUVABLE");
        assertThat(monde.absences.nombre()).isZero();
        assertThat(monde.signalements).isEmpty();
    }

    @Test
    void declarer_deux_fois_le_meme_jour_est_refuse_meme_si_la_premiere_est_annulee() {
        AbsencePatient premiere = monde.declaree(AMRANI, SEANCE, null);
        assertRefus(() -> monde.declarer.executer(CENTRE, monde.commande(AMRANI, SEANCE, null, null), SANS_DROIT),
                "ABSENCE_EXISTANTE");
        monde.annuler.executer(CENTRE, premiere.id(), "Erreur", SANS_DROIT);

        assertRefus(() -> monde.declarer.executer(CENTRE, monde.commande(AMRANI, SEANCE, null, null), SANS_DROIT),
                "ABSENCE_EXISTANTE");
        assertThat(monde.absences.nombre()).isEqualTo(1);
    }

    @Test
    void declarer_une_absence_existante_prime_sur_les_dates_la_periode_facturee_et_la_seance_realisee() {
        monde.declaree(AMRANI, AUJOURDHUI.minusDays(50), null);
        monde.facturer(AMRANI, AUJOURDHUI.minusDays(50));

        assertRefus(() -> monde.declarer.executer(CENTRE, monde.commande(AMRANI, AUJOURDHUI.minusDays(50), null, null), SANS_DROIT),
                "ABSENCE_EXISTANTE");
    }

    @Test
    void declarer_exige_dans_l_ordre_la_periode_ouverte_puis_l_absence_de_seance_realisee_puis_les_dates() {
        monde.facturer(AMRANI, SEANCE);
        monde.suivi.poserLaSeance(CENTRE, AMRANI, SEANCE, "VALIDEE");
        assertRefus(() -> monde.declarer.executer(CENTRE, monde.commande(AMRANI, SEANCE, null, null), SANS_DROIT),
                "ABSENCE_PERIODE_FACTUREE");

        monde.suivi.poserLaSeance(CENTRE, BELKACEM, SEANCE, "SIGNEE");
        assertRefus(() -> monde.declarer.executer(CENTRE, monde.commande(BELKACEM, SEANCE, null, null), SANS_DROIT),
                "ABSENCE_SEANCE_REALISEE");

        assertRefus(() -> monde.declarer.executer(CENTRE, monde.commande(BELKACEM, AUJOURDHUI.plusDays(1), null, null), SANS_DROIT),
                "ABSENCE_DATE_FUTURE");
        assertRefus(() -> monde.declarer.executer(CENTRE, monde.commande(BELKACEM, AUJOURDHUI.minusDays(61), null, null), SANS_DROIT),
                "ABSENCE_DELAI_DEPASSE");
        assertThat(monde.absences.nombre()).isZero();
        assertThat(monde.signalements).isEmpty();
    }

    @Test
    void declarer_une_seance_creee_ou_absente_n_empeche_pas_l_absence() {
        monde.suivi.poserLaSeance(CENTRE, AMRANI, SEANCE, "CREE");
        monde.suivi.poserLaSeance(CENTRE, AMRANI, SEANCE.plusDays(1), "ABSENT");

        monde.declarer.executer(CENTRE, monde.commande(AMRANI, SEANCE, null, null), SANS_DROIT);
        monde.declarer.executer(CENTRE, monde.commande(AMRANI, SEANCE.plusDays(1), null, null), SANS_DROIT);

        assertThat(monde.absences.nombre()).isEqualTo(2);
    }

    @Test
    void declarer_un_commentaire_trop_long_ou_un_autre_sans_commentaire_ecrit_rien() {
        assertThatThrownBy(() -> monde.declarer.executer(CENTRE, monde.commande(AMRANI, SEANCE, MotifAbsence.VOYAGE, "x".repeat(1001)),
                SANS_DROIT)).isInstanceOf(SaisieInvalide.class);
        assertRefus(() -> monde.declarer.executer(CENTRE, monde.commande(AMRANI, SEANCE, MotifAbsence.AUTRE, null), SANS_DROIT),
                "ABSENCE_COMMENTAIRE_REQUIS");

        assertThat(monde.absences.nombre()).isZero();
        assertThat(monde.signalements).isEmpty();
    }

    // --- qualifier

    @Test
    void qualifier_ecrit_le_motif_trace_l_auteur_et_signale_sans_recalculer_la_valeur() {
        AbsencePatient absence = monde.declaree(AMRANI, SEANCE, null);
        monde.forfaits.clear();
        monde.taux = new BigDecimal("9");

        LigneDuSuivi ligne = monde.qualifier.executer(CENTRE, absence.id(), MotifAbsence.NON_JUSTIFIEE, null, SANS_DROIT);

        AbsencePatient lue = monde.absences.lire(absence.id()).orElseThrow();
        assertThat(lue.statut()).isEqualTo(StatutAbsence.NON_JUSTIFIEE);
        assertThat(lue.qualifieePar()).isEqualTo(SECRETAIRE);
        assertThat(lue.valorisation()).isEqualTo(absence.valorisation());
        assertThat(ligne.absence().statut()).isEqualTo(StatutAbsence.NON_JUSTIFIEE);
        assertThat(ligne.enRetard()).isFalse();
        assertThat(monde.signalements).hasSize(2);
    }

    @Test
    void qualifier_une_absence_inconnue_ou_d_un_autre_centre_est_introuvable() {
        AbsencePatient absence = monde.declaree(AMRANI, SEANCE, null);

        assertThatThrownBy(() -> monde.qualifier.executer(AUTRE_CENTRE, absence.id(), MotifAbsence.VOYAGE, null, AVEC_DROIT))
                .isInstanceOf(AbsenceIntrouvable.class);
        assertThatThrownBy(() -> monde.qualifier.executer(CENTRE, new AbsenceId(UUID.randomUUID()), MotifAbsence.VOYAGE, null,
                AVEC_DROIT)).isInstanceOf(AbsenceIntrouvable.class);
    }

    @Test
    void qualifier_une_correction_sans_droit_est_refusee_sans_rien_ecrire() {
        AbsencePatient absence = monde.declaree(AMRANI, SEANCE, MotifAbsence.VOYAGE);
        int signalementsAvant = monde.signalements.size();

        assertRefus(() -> monde.qualifier.executer(CENTRE, absence.id(), MotifAbsence.MALADIE, "x", SANS_DROIT),
                "ABSENCE_CORRECTION_INTERDITE");

        assertThat(monde.absences.lire(absence.id()).orElseThrow().motif()).isEqualTo(MotifAbsence.VOYAGE);
        assertThat(monde.signalements).hasSize(signalementsAvant);
        monde.qualifier.executer(CENTRE, absence.id(), MotifAbsence.MALADIE, "x", AVEC_DROIT);
        assertThat(monde.absences.lire(absence.id()).orElseThrow().motif()).isEqualTo(MotifAbsence.MALADIE);
    }

    @Test
    void qualifier_dans_une_periode_facturee_est_refuse() {
        AbsencePatient absence = monde.declaree(AMRANI, SEANCE, null);
        monde.facturer(AMRANI, SEANCE);

        assertRefus(() -> monde.qualifier.executer(CENTRE, absence.id(), MotifAbsence.VOYAGE, null, AVEC_DROIT),
                "ABSENCE_PERIODE_FACTUREE");
    }

    // --- rattraper

    @Test
    void rattraper_par_une_seance_realisee_du_patient_passe_l_absence_a_rattrapee() {
        AbsencePatient absence = monde.declaree(AMRANI, SEANCE, MotifAbsence.MALADIE);
        monde.suivi.poserLaSeance(CENTRE, AMRANI, SEANCE.plusDays(2), "VALIDEE");

        LigneDuSuivi ligne = monde.rattraper.executer(CENTRE, absence.id(), SEANCE.plusDays(2), AVEC_DROIT);

        AbsencePatient lue = monde.absences.lire(absence.id()).orElseThrow();
        assertThat(lue.statut()).isEqualTo(StatutAbsence.RATTRAPEE);
        assertThat(lue.dateDeRattrapage()).isEqualTo(SEANCE.plusDays(2));
        assertThat(lue.modifieePar()).isEqualTo(AVEC_DROIT.utilisateur());
        assertThat(ligne.absence().dateDeRattrapage()).isEqualTo(SEANCE.plusDays(2));
    }

    @Test
    void rattraper_exige_le_droit_de_correction_puis_l_etat_puis_les_dates_puis_la_seance_puis_l_unicite() {
        AbsencePatient absence = monde.declaree(AMRANI, SEANCE, null);
        monde.suivi.poserLaSeance(CENTRE, AMRANI, SEANCE.plusDays(2), "VALIDEE");

        assertRefus(() -> monde.rattraper.executer(CENTRE, absence.id(), SEANCE.plusDays(2), SANS_DROIT),
                "ABSENCE_CORRECTION_INTERDITE");
        assertRefus(() -> monde.rattraper.executer(CENTRE, absence.id(), SEANCE, AVEC_DROIT), "ABSENCE_RATTRAPAGE_ANTERIEUR");
        assertRefus(() -> monde.rattraper.executer(CENTRE, absence.id(), AUJOURDHUI.plusDays(1), AVEC_DROIT), "ABSENCE_DATE_FUTURE");
        assertRefus(() -> monde.rattraper.executer(CENTRE, absence.id(), SEANCE.plusDays(3), AVEC_DROIT),
                "ABSENCE_RATTRAPAGE_SANS_SEANCE");
        assertThat(monde.absences.lire(absence.id()).orElseThrow().statut()).isEqualTo(StatutAbsence.A_QUALIFIER);
    }

    @Test
    void rattraper_une_seance_ne_rattrape_qu_une_absence_du_patient() {
        AbsencePatient premiere = monde.declaree(AMRANI, SEANCE, null);
        AbsencePatient seconde = monde.declaree(AMRANI, SEANCE.plusDays(1), null);
        monde.suivi.poserLaSeance(CENTRE, AMRANI, SEANCE.plusDays(3), "VALIDEE");
        monde.suivi.poserLaSeance(CENTRE, AMRANI, SEANCE.plusDays(4), "VALIDEE");
        monde.rattraper.executer(CENTRE, premiere.id(), SEANCE.plusDays(3), AVEC_DROIT);

        assertRefus(() -> monde.rattraper.executer(CENTRE, seconde.id(), SEANCE.plusDays(3), AVEC_DROIT),
                "ABSENCE_RATTRAPAGE_DEJA_UTILISEE");
        monde.rattraper.executer(CENTRE, seconde.id(), SEANCE.plusDays(4), AVEC_DROIT);

        assertThat(monde.absences.lire(seconde.id()).orElseThrow().dateDeRattrapage()).isEqualTo(SEANCE.plusDays(4));
    }

    @Test
    void rattraper_la_seance_d_un_autre_patient_ou_d_un_autre_centre_ne_compte_pas() {
        AbsencePatient absence = monde.declaree(AMRANI, SEANCE, null);
        monde.suivi.poserLaSeance(CENTRE, BELKACEM, SEANCE.plusDays(2), "VALIDEE");
        monde.suivi.poserLaSeance(AUTRE_CENTRE, AMRANI, SEANCE.plusDays(2), "VALIDEE");

        assertRefus(() -> monde.rattraper.executer(CENTRE, absence.id(), SEANCE.plusDays(2), AVEC_DROIT),
                "ABSENCE_RATTRAPAGE_SANS_SEANCE");
        assertThatThrownBy(() -> monde.rattraper.executer(AUTRE_CENTRE, absence.id(), SEANCE.plusDays(2), AVEC_DROIT))
                .isInstanceOf(AbsenceIntrouvable.class);
    }

    @Test
    void rattraper_une_absence_annulee_est_non_modifiable_avant_tout_controle_de_seance() {
        AbsencePatient absence = monde.declaree(AMRANI, SEANCE, null);
        monde.annuler.executer(CENTRE, absence.id(), "Erreur", SANS_DROIT);

        assertThatThrownBy(() -> monde.rattraper.executer(CENTRE, absence.id(), SEANCE.plusDays(2), AVEC_DROIT))
                .isInstanceOf(AbsenceNonModifiable.class);
    }

    // --- annuler

    @Test
    void annuler_une_absence_a_qualifier_par_n_importe_qui_remplace_le_commentaire_et_signale() {
        LigneDuSuivi declaree = monde.declarer.executer(CENTRE, monde.commande(AMRANI, SEANCE, null, "ancien"), SANS_DROIT);
        AbsencePatient absence = monde.absences.lire(declaree.absence().id()).orElseThrow();

        LigneDuSuivi ligne = monde.annuler.executer(CENTRE, absence.id(), "  Patient présent ", SANS_DROIT);

        AbsencePatient lue = monde.absences.lire(absence.id()).orElseThrow();
        assertThat(lue.statut()).isEqualTo(StatutAbsence.ANNULEE);
        assertThat(lue.commentaire()).isEqualTo("Patient présent");
        assertThat(ligne.absence().statut()).isEqualTo(StatutAbsence.ANNULEE);
        assertThat(monde.signalements).hasSize(2);
    }

    @Test
    void annuler_une_absence_qualifiee_exige_le_droit_et_une_rattrapee_ne_s_annule_pas() {
        AbsencePatient qualifiee = monde.declaree(AMRANI, SEANCE, MotifAbsence.VOYAGE);
        AbsencePatient rattrapee = monde.declaree(AMRANI, SEANCE.plusDays(1), null);
        monde.suivi.poserLaSeance(CENTRE, AMRANI, SEANCE.plusDays(3), "VALIDEE");
        monde.rattraper.executer(CENTRE, rattrapee.id(), SEANCE.plusDays(3), AVEC_DROIT);

        assertRefus(() -> monde.annuler.executer(CENTRE, qualifiee.id(), "Erreur", SANS_DROIT), "ABSENCE_CORRECTION_INTERDITE");
        assertThatThrownBy(() -> monde.annuler.executer(CENTRE, rattrapee.id(), "Erreur", AVEC_DROIT))
                .isInstanceOf(AbsenceNonModifiable.class);

        AbsencePatient lue = monde.absences.lire(rattrapee.id()).orElseThrow();
        assertThat(lue.statut()).isEqualTo(StatutAbsence.RATTRAPEE);
        assertThat(lue.dateDeRattrapage()).isEqualTo(SEANCE.plusDays(3));
        monde.annuler.executer(CENTRE, qualifiee.id(), "Erreur", AVEC_DROIT);
        assertThat(monde.absences.lire(qualifiee.id()).orElseThrow().statut()).isEqualTo(StatutAbsence.ANNULEE);
    }

    @Test
    void annuler_deux_fois_ou_sans_commentaire_est_refuse() {
        AbsencePatient absence = monde.declaree(AMRANI, SEANCE, null);

        assertRefus(() -> monde.annuler.executer(CENTRE, absence.id(), " ", SANS_DROIT), "ABSENCE_COMMENTAIRE_REQUIS");
        monde.annuler.executer(CENTRE, absence.id(), "Erreur", SANS_DROIT);
        assertRefus(() -> monde.annuler.executer(CENTRE, absence.id(), "Encore", AVEC_DROIT), "ABSENCE_DEJA_ANNULEE");
        assertThatThrownBy(() -> monde.annuler.executer(AUTRE_CENTRE, absence.id(), "x", AVEC_DROIT))
                .isInstanceOf(AbsenceIntrouvable.class);
    }

    @Test
    void annuler_dans_une_periode_facturee_est_refuse_apres_le_droit_et_l_etat() {
        AbsencePatient absence = monde.declaree(AMRANI, SEANCE, null);
        monde.facturer(AMRANI, SEANCE);

        assertRefus(() -> monde.annuler.executer(CENTRE, absence.id(), "Erreur", SANS_DROIT), "ABSENCE_PERIODE_FACTUREE");
    }

    // --- retirer l'absence d'une exclusion

    @Test
    void retirer_supprime_l_absence_d_une_exclusion_non_travaillee_et_la_date_redevient_libre() {
        AbsencePatient absence = monde.declaree(AMRANI, SEANCE, MotifAbsence.VOYAGE);

        monde.retirer.executer(CENTRE, absence.id());

        assertThat(monde.absences.lire(absence.id())).isEmpty();
        monde.declarer.executer(CENTRE, monde.commande(AMRANI, SEANCE, null, null), SANS_DROIT);
        assertThat(monde.absences.nombre()).isEqualTo(1);
    }

    @Test
    void retirer_une_absence_deja_supprimee_ou_d_un_autre_centre_est_sans_effet() {
        AbsencePatient absence = monde.declaree(AMRANI, SEANCE, null);

        monde.retirer.executer(AUTRE_CENTRE, absence.id());
        monde.retirer.executer(CENTRE, new AbsenceId(UUID.randomUUID()));

        assertThat(monde.absences.nombre()).isEqualTo(1);
    }

    @Test
    void retirer_supprime_aussi_une_absence_requalifiee_ou_rattrapee_mais_pas_une_annulee_heritee() {
        AbsencePatient requalifiee = monde.declaree(AMRANI, SEANCE, MotifAbsence.VOYAGE);
        monde.plusTard();
        monde.qualifier.executer(CENTRE, requalifiee.id(), MotifAbsence.MALADIE, "Contrôle", AVEC_DROIT);
        AbsencePatient annulee = monde.declaree(AMRANI, SEANCE.plusDays(1), null);
        monde.annuler.executer(CENTRE, annulee.id(), "Erreur", SANS_DROIT);

        monde.retirer.executer(CENTRE, requalifiee.id());
        assertRefus(() -> monde.retirer.executer(CENTRE, annulee.id()), "ABSENCE_D_EXCLUSION_MODIFIEE");

        assertThat(monde.absences.lire(requalifiee.id())).isEmpty();
        assertThat(monde.absences.nombre()).isEqualTo(1);
    }

    @Test
    void annuler_l_absence_d_une_exclusion_est_refuse_meme_pour_l_administrateur() {
        AbsencePatient absence = monde.declaree(AMRANI, SEANCE, MotifAbsence.VOYAGE);
        monde.absencesDExclusion.add(absence.id());
        int signalementsAvant = monde.signalements.size();

        assertRefus(() -> monde.annuler.executer(CENTRE, absence.id(), "Erreur", AVEC_DROIT), "ABSENCE_D_EXCLUSION");

        assertThat(monde.absences.lire(absence.id()).orElseThrow().statut()).isEqualTo(StatutAbsence.JUSTIFIEE);
        assertThat(monde.signalements).hasSize(signalementsAvant);
        monde.qualifier.executer(CENTRE, absence.id(), MotifAbsence.MALADIE, "Contrôle", AVEC_DROIT);
    }

    @Test
    void rattraper_signale_la_saisie_au_medecin() {
        AbsencePatient absence = monde.declaree(AMRANI, SEANCE, null);
        monde.suivi.poserLaSeance(CENTRE, AMRANI, SEANCE.plusDays(2), "VALIDEE");
        int avant = monde.signalements.size();

        monde.rattraper.executer(CENTRE, absence.id(), SEANCE.plusDays(2), AVEC_DROIT);

        assertThat(monde.signalements).hasSize(avant + 1).last()
                .isEqualTo(CENTRE.valeur() + "/" + AMRANI.valeur() + "/" + SEANCE);
    }

    @Test
    void chaque_cas_d_usage_est_une_seule_transaction() {
        AbsencePatient absence = monde.declaree(AMRANI, SEANCE, null);
        int avant = monde.unite.executions();

        monde.annuler.executer(CENTRE, absence.id(), "Erreur", SANS_DROIT);
        LocalDate jour = SEANCE.plusDays(1);
        monde.declarer.executer(CENTRE, monde.commande(BELKACEM, jour, null, null), SANS_DROIT);

        assertThat(monde.unite.executions() - avant).isEqualTo(2);
    }
}
