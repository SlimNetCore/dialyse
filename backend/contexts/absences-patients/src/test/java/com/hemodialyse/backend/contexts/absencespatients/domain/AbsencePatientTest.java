package com.hemodialyse.backend.contexts.absencespatients.domain;

import com.hemodialyse.backend.contexts.absencespatients.domain.EvenementDAbsence.AbsenceAnnulee;
import com.hemodialyse.backend.contexts.absencespatients.domain.EvenementDAbsence.AbsenceDeclaree;
import com.hemodialyse.backend.contexts.absencespatients.domain.EvenementDAbsence.AbsenceQualifiee;
import com.hemodialyse.backend.contexts.absencespatients.domain.EvenementDAbsence.AbsenceRattrapee;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.AbsenceId;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.ForfaitId;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.PatientId;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.UtilisateurId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cycle de vie d'une absence de patient (matrice invariant -> test du tactique : A2, A3, A4, A5, A6, A7, A9, A16) : domaine
 * pur, sans framework. Les codes de refus sont ceux du legacy.
 */
class AbsencePatientTest {

    private static final CentreId CENTRE = new CentreId(new UUID(1, 1));
    private static final PatientId PATIENT = new PatientId(new UUID(2, 1));
    private static final UtilisateurId SECRETAIRE = new UtilisateurId(new UUID(3, 1));
    private static final UtilisateurId MEDECIN = new UtilisateurId(new UUID(3, 2));
    private static final LocalDate AUJOURDHUI = LocalDate.of(2026, 10, 22);
    private static final LocalDate SEANCE = AUJOURDHUI.minusDays(5);
    private static final Instant INSTANT = Instant.parse("2026-10-22T08:00:00Z");
    private static final Instant PLUS_TARD = Instant.parse("2026-10-22T09:30:00Z");
    private static final PeriodesFacturees AUCUNE_FACTURE = (centre, patient, jour) -> false;
    private static final PeriodesFacturees TOUT_FACTURE = (centre, patient, jour) -> true;
    private static final Valorisation VALEUR = Valorisation.depuis(
            new ForfaitApplicable(new ForfaitId(new UUID(4, 1)), "Forfait HD", new BigDecimal("10000.00")), new BigDecimal("19"));

    private static AbsencePatient declaree(MotifAbsence motif, String commentaire) {
        return AbsencePatient.declarer(new AbsenceId(new UUID(5, 1)), DateOuverte.exiger(AUCUNE_FACTURE, CENTRE, PATIENT, SEANCE),
                motif, commentaire, VALEUR, SECRETAIRE, AUJOURDHUI, INSTANT);
    }

    private static AbsencePatient aQualifier() {
        return declaree(null, null);
    }

    private static AbsencePatient justifiee() {
        return declaree(MotifAbsence.MALADIE, null);
    }

    private static AbsencePatient rattrapee() {
        AbsencePatient absence = aQualifier();
        absence.rattraper(SEANCE.plusDays(2), MEDECIN, DroitDeCorrection.ACCORDE, AUCUNE_FACTURE, faits(true, false), AUJOURDHUI,
                PLUS_TARD);
        return absence;
    }

    private static FaitsDuRattrapage faits(boolean seanceRealisee, boolean dejaUtilisee) {
        return new FaitsDuRattrapage() {
            @Override
            public boolean seanceRealisee(LocalDate jour) {
                return seanceRealisee;
            }

            @Override
            public boolean dejaUtilisee(LocalDate jour) {
                return dejaUtilisee;
            }
        };
    }

    private static void assertRefus(Runnable action, String code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(RegleDAbsenceViolee.class, e -> assertThat(e.code()).isEqualTo(code));
    }

    @Test
    void sans_motif_une_absence_declaree_est_a_qualifier_et_garde_la_valeur_figee() {
        AbsencePatient absence = aQualifier();

        assertThat(absence.statut()).isEqualTo(StatutAbsence.A_QUALIFIER);
        assertThat(absence.source()).isEqualTo(SourceAbsence.DECLAREE);
        assertThat(absence.motif()).isNull();
        assertThat(absence.declareePar()).isEqualTo(SECRETAIRE);
        assertThat(absence.declareeLe()).isEqualTo(INSTANT);
        assertThat(absence.qualifieeLe()).isNull();
        assertThat(absence.modifieeLe()).isNull();
        assertThat(absence.valorisation()).isEqualTo(VALEUR);
        assertThat(absence.tirerLesEvenements()).containsExactly(new AbsenceDeclaree(absence.id(), CENTRE, PATIENT, SEANCE,
                SECRETAIRE, INSTANT));
    }

    @Test
    void avec_un_motif_une_absence_declaree_est_qualifiee_d_emblee_par_son_declarant() {
        AbsencePatient absence = justifiee();

        assertThat(absence.statut()).isEqualTo(StatutAbsence.JUSTIFIEE);
        assertThat(absence.motif()).isEqualTo(MotifAbsence.MALADIE);
        assertThat(absence.qualifieePar()).isEqualTo(SECRETAIRE);
        assertThat(absence.qualifieeLe()).isEqualTo(INSTANT);
        assertThat(absence.modifieePar()).isEqualTo(SECRETAIRE);
        assertThat(absence.modifieeLe()).isEqualTo(INSTANT);
        assertThat(absence.tirerLesEvenements()).extracting(Object::getClass)
                .containsExactly(AbsenceDeclaree.class, AbsenceQualifiee.class);
    }

    @ParameterizedTest
    @EnumSource(MotifAbsence.class)
    void seul_le_motif_non_justifie_donne_une_absence_non_justifiee(MotifAbsence motif) {
        AbsencePatient absence = declaree(motif, "Précision");

        assertThat(absence.statut()).isEqualTo(motif == MotifAbsence.NON_JUSTIFIEE ? StatutAbsence.NON_JUSTIFIEE : StatutAbsence.JUSTIFIEE);
    }

    @Test
    void un_commentaire_donne_sans_motif_est_conserve_et_rogne() {
        AbsencePatient absence = declaree(null, "  Appel de la famille  ");

        assertThat(absence.commentaire()).isEqualTo("Appel de la famille");
        assertThat(absence.statut()).isEqualTo(StatutAbsence.A_QUALIFIER);
        assertThat(absence.modifieeLe()).isNull();
    }

    @Test
    void un_commentaire_blanc_est_absent() {
        assertThat(declaree(MotifAbsence.VOYAGE, "   ").commentaire()).isNull();
    }

    @Test
    void le_motif_autre_exige_un_commentaire_a_la_declaration() {
        assertRefus(() -> declaree(MotifAbsence.AUTRE, "  "), "ABSENCE_COMMENTAIRE_REQUIS");
        assertThat(declaree(MotifAbsence.AUTRE, "Précision").statut()).isEqualTo(StatutAbsence.JUSTIFIEE);
    }

    @Test
    void la_date_de_la_veille_a_soixante_jours_est_acceptee_mais_pas_demain_ni_soixante_et_un_jours() {
        PeriodesFacturees ouvertes = AUCUNE_FACTURE;
        assertThat(AbsencePatient.declarer(new AbsenceId(new UUID(5, 2)), DateOuverte.exiger(ouvertes, CENTRE, PATIENT, AUJOURDHUI),
                null, null, VALEUR, SECRETAIRE, AUJOURDHUI, INSTANT).dateDeSeance()).isEqualTo(AUJOURDHUI);
        assertThat(AbsencePatient.declarer(new AbsenceId(new UUID(5, 2)),
                DateOuverte.exiger(ouvertes, CENTRE, PATIENT, AUJOURDHUI.minusDays(60)), null, null, VALEUR, SECRETAIRE, AUJOURDHUI,
                INSTANT).dateDeSeance()).isEqualTo(AUJOURDHUI.minusDays(60));
        assertRefus(() -> AbsencePatient.declarer(new AbsenceId(new UUID(5, 2)),
                DateOuverte.exiger(ouvertes, CENTRE, PATIENT, AUJOURDHUI.plusDays(1)), null, null, VALEUR, SECRETAIRE, AUJOURDHUI,
                INSTANT), "ABSENCE_DATE_FUTURE");
        assertRefus(() -> AbsencePatient.declarer(new AbsenceId(new UUID(5, 2)),
                DateOuverte.exiger(ouvertes, CENTRE, PATIENT, AUJOURDHUI.minusDays(61)), null, null, VALEUR, SECRETAIRE, AUJOURDHUI,
                INSTANT), "ABSENCE_DELAI_DEPASSE");
    }

    @Test
    void une_periode_facturee_ferme_la_date() {
        assertRefus(() -> DateOuverte.exiger(TOUT_FACTURE, CENTRE, PATIENT, SEANCE), "ABSENCE_PERIODE_FACTUREE");
    }

    @Test
    void un_commentaire_de_plus_de_mille_caracteres_est_refuse_en_saisie_invalide() {
        assertThatThrownBy(() -> declaree(MotifAbsence.VOYAGE, "x".repeat(1001))).isInstanceOf(SaisieInvalide.class);
        assertThat(declaree(MotifAbsence.VOYAGE, "x".repeat(1000)).commentaire()).hasSize(1000);
    }

    // --- qualification

    @Test
    void qualifier_une_absence_a_qualifier_ne_demande_pas_le_droit_de_correction() {
        AbsencePatient absence = aQualifier();

        absence.qualifier(MotifAbsence.NON_JUSTIFIEE, null, SECRETAIRE, DroitDeCorrection.REFUSE, AUCUNE_FACTURE, PLUS_TARD);

        assertThat(absence.statut()).isEqualTo(StatutAbsence.NON_JUSTIFIEE);
        assertThat(absence.qualifieePar()).isEqualTo(SECRETAIRE);
        assertThat(absence.qualifieeLe()).isEqualTo(PLUS_TARD);
        assertThat(absence.modifieeLe()).isEqualTo(PLUS_TARD);
        assertThat(absence.valorisation()).isEqualTo(VALEUR);
    }

    @Test
    void requalifier_exige_le_droit_de_correction_puis_un_commentaire_qui_remplace_l_ancien() {
        AbsencePatient absence = declaree(MotifAbsence.VOYAGE, "Ancien");

        assertRefus(() -> absence.qualifier(MotifAbsence.MALADIE, "Nouveau", SECRETAIRE, DroitDeCorrection.REFUSE, AUCUNE_FACTURE,
                PLUS_TARD), "ABSENCE_CORRECTION_INTERDITE");
        assertRefus(() -> absence.qualifier(MotifAbsence.MALADIE, null, MEDECIN, DroitDeCorrection.ACCORDE, AUCUNE_FACTURE,
                PLUS_TARD), "ABSENCE_COMMENTAIRE_REQUIS");
        assertThat(absence.motif()).isEqualTo(MotifAbsence.VOYAGE);

        absence.qualifier(MotifAbsence.MALADIE, "Nouveau", MEDECIN, DroitDeCorrection.ACCORDE, AUCUNE_FACTURE, PLUS_TARD);

        assertThat(absence.motif()).isEqualTo(MotifAbsence.MALADIE);
        assertThat(absence.commentaire()).isEqualTo("Nouveau");
        assertThat(absence.qualifieePar()).isEqualTo(MEDECIN);
    }

    @Test
    void une_qualification_sans_commentaire_garde_le_commentaire_deja_present() {
        AbsencePatient absence = declaree(null, "Appel de la famille");

        absence.qualifier(MotifAbsence.TRANSPORT, null, SECRETAIRE, DroitDeCorrection.REFUSE, AUCUNE_FACTURE, PLUS_TARD);

        assertThat(absence.commentaire()).isEqualTo("Appel de la famille");
        assertThat(absence.statut()).isEqualTo(StatutAbsence.JUSTIFIEE);
    }

    @Test
    void qualifier_sans_motif_ou_avec_autre_sans_commentaire_est_refuse() {
        AbsencePatient absence = aQualifier();

        assertRefus(() -> absence.qualifier(null, "x", SECRETAIRE, DroitDeCorrection.REFUSE, AUCUNE_FACTURE, PLUS_TARD),
                "ABSENCE_MOTIF_REQUIS");
        assertRefus(() -> absence.qualifier(MotifAbsence.AUTRE, " ", SECRETAIRE, DroitDeCorrection.REFUSE, AUCUNE_FACTURE,
                PLUS_TARD), "ABSENCE_COMMENTAIRE_REQUIS");
        assertThat(absence.statut()).isEqualTo(StatutAbsence.A_QUALIFIER);
    }

    @Test
    void une_absence_rattrapee_ou_annulee_n_est_plus_qualifiable() {
        AbsencePatient rattrapee = rattrapee();
        AbsencePatient annulee = aQualifier();
        annulee.annuler("Erreur de saisie", SECRETAIRE, DroitDeCorrection.REFUSE, false, AUCUNE_FACTURE, PLUS_TARD);

        for (AbsencePatient absence : List.of(rattrapee, annulee)) {
            assertThatThrownBy(() -> absence.qualifier(MotifAbsence.MALADIE, "x", MEDECIN, DroitDeCorrection.ACCORDE,
                    AUCUNE_FACTURE, PLUS_TARD)).isInstanceOf(AbsenceNonModifiable.class);
        }
    }

    @Test
    void le_droit_de_correction_passe_avant_l_etat_et_l_etat_avant_la_periode_facturee() {
        AbsencePatient annulee = aQualifier();
        annulee.annuler("Erreur de saisie", SECRETAIRE, DroitDeCorrection.REFUSE, false, AUCUNE_FACTURE, PLUS_TARD);

        assertRefus(() -> annulee.qualifier(MotifAbsence.MALADIE, "x", SECRETAIRE, DroitDeCorrection.REFUSE, TOUT_FACTURE, PLUS_TARD),
                "ABSENCE_CORRECTION_INTERDITE");
        assertThatThrownBy(() -> annulee.qualifier(MotifAbsence.MALADIE, "x", MEDECIN, DroitDeCorrection.ACCORDE, TOUT_FACTURE,
                PLUS_TARD)).isInstanceOf(AbsenceNonModifiable.class);
        assertRefus(() -> aQualifier().qualifier(MotifAbsence.MALADIE, "x", MEDECIN, DroitDeCorrection.ACCORDE, TOUT_FACTURE,
                PLUS_TARD), "ABSENCE_PERIODE_FACTUREE");
    }

    // --- rattrapage

    @Test
    void rattraper_enregistre_la_date_et_ne_touche_ni_le_motif_ni_la_valeur() {
        AbsencePatient absence = justifiee();

        absence.rattraper(SEANCE.plusDays(2), MEDECIN, DroitDeCorrection.ACCORDE, AUCUNE_FACTURE, faits(true, false), AUJOURDHUI,
                PLUS_TARD);

        assertThat(absence.statut()).isEqualTo(StatutAbsence.RATTRAPEE);
        assertThat(absence.dateDeRattrapage()).isEqualTo(SEANCE.plusDays(2));
        assertThat(absence.motif()).isEqualTo(MotifAbsence.MALADIE);
        assertThat(absence.valorisation()).isEqualTo(VALEUR);
        assertThat(absence.modifieePar()).isEqualTo(MEDECIN);
        assertThat(absence.statut().comptabilisee()).isFalse();
        assertThat(absence.tirerLesEvenements()).last().isEqualTo(new AbsenceRattrapee(absence.id(), CENTRE, PATIENT, SEANCE,
                MEDECIN, PLUS_TARD, SEANCE.plusDays(2)));
    }

    @Test
    void rattraper_exige_le_droit_de_correction_meme_pour_une_absence_a_qualifier() {
        assertRefus(() -> aQualifier().rattraper(SEANCE.plusDays(1), SECRETAIRE, DroitDeCorrection.REFUSE, AUCUNE_FACTURE,
                faits(true, false), AUJOURDHUI, PLUS_TARD), "ABSENCE_CORRECTION_INTERDITE");
    }

    @Test
    void la_date_de_rattrapage_est_requise_posterieure_a_la_seance_et_pas_future() {
        AbsencePatient absence = aQualifier();
        FaitsDuRattrapage faits = faits(true, false);

        assertRefus(() -> absence.rattraper(null, MEDECIN, DroitDeCorrection.ACCORDE, AUCUNE_FACTURE, faits, AUJOURDHUI, PLUS_TARD),
                "ABSENCE_RATTRAPAGE_DATE_REQUISE");
        assertRefus(() -> absence.rattraper(SEANCE, MEDECIN, DroitDeCorrection.ACCORDE, AUCUNE_FACTURE, faits, AUJOURDHUI,
                PLUS_TARD), "ABSENCE_RATTRAPAGE_ANTERIEUR");
        assertRefus(() -> absence.rattraper(SEANCE.minusDays(1), MEDECIN, DroitDeCorrection.ACCORDE, AUCUNE_FACTURE, faits,
                AUJOURDHUI, PLUS_TARD), "ABSENCE_RATTRAPAGE_ANTERIEUR");
        assertRefus(() -> absence.rattraper(AUJOURDHUI.plusDays(1), MEDECIN, DroitDeCorrection.ACCORDE, AUCUNE_FACTURE, faits,
                AUJOURDHUI, PLUS_TARD), "ABSENCE_DATE_FUTURE");
        absence.rattraper(AUJOURDHUI, MEDECIN, DroitDeCorrection.ACCORDE, AUCUNE_FACTURE, faits, AUJOURDHUI, PLUS_TARD);
        assertThat(absence.dateDeRattrapage()).isEqualTo(AUJOURDHUI);
    }

    @Test
    void le_rattrapage_exige_une_seance_realisee_non_deja_utilisee_par_une_autre_absence() {
        AbsencePatient absence = aQualifier();

        assertRefus(() -> absence.rattraper(SEANCE.plusDays(1), MEDECIN, DroitDeCorrection.ACCORDE, AUCUNE_FACTURE,
                faits(false, false), AUJOURDHUI, PLUS_TARD), "ABSENCE_RATTRAPAGE_SANS_SEANCE");
        assertRefus(() -> absence.rattraper(SEANCE.plusDays(1), MEDECIN, DroitDeCorrection.ACCORDE, AUCUNE_FACTURE,
                faits(true, true), AUJOURDHUI, PLUS_TARD), "ABSENCE_RATTRAPAGE_DEJA_UTILISEE");
        assertThat(absence.statut()).isEqualTo(StatutAbsence.A_QUALIFIER);
        assertThat(absence.dateDeRattrapage()).isNull();
    }

    @Test
    void une_absence_rattrapee_ne_se_rattrape_plus_et_les_faits_ne_sont_pas_consultes_avant_les_dates() {
        AbsencePatient absence = rattrapee();

        assertThatThrownBy(() -> absence.rattraper(SEANCE.plusDays(3), MEDECIN, DroitDeCorrection.ACCORDE, AUCUNE_FACTURE,
                faits(true, false), AUJOURDHUI, PLUS_TARD)).isInstanceOf(AbsenceNonModifiable.class);
        assertRefus(() -> aQualifier().rattraper(SEANCE.plusDays(3), MEDECIN, DroitDeCorrection.ACCORDE, TOUT_FACTURE,
                faits(true, false), AUJOURDHUI, PLUS_TARD), "ABSENCE_PERIODE_FACTUREE");
    }

    // --- annulation

    @Test
    void annuler_une_absence_a_qualifier_ne_demande_pas_le_droit_de_correction_et_remplace_le_commentaire() {
        AbsencePatient absence = declaree(null, "Ancien");

        absence.annuler("  Patient présent  ", SECRETAIRE, DroitDeCorrection.REFUSE, false, AUCUNE_FACTURE, PLUS_TARD);

        assertThat(absence.statut()).isEqualTo(StatutAbsence.ANNULEE);
        assertThat(absence.commentaire()).isEqualTo("Patient présent");
        assertThat(absence.modifieePar()).isEqualTo(SECRETAIRE);
        assertThat(absence.statut().comptabilisee()).isFalse();
        assertThat(absence.tirerLesEvenements()).last().isEqualTo(new AbsenceAnnulee(absence.id(), CENTRE, PATIENT, SEANCE,
                SECRETAIRE, PLUS_TARD));
    }

    @Test
    void annuler_une_absence_qualifiee_exige_le_droit_de_correction_et_garde_le_motif() {
        AbsencePatient absence = justifiee();

        assertRefus(() -> absence.annuler("Erreur", SECRETAIRE, DroitDeCorrection.REFUSE, false, AUCUNE_FACTURE, PLUS_TARD),
                "ABSENCE_CORRECTION_INTERDITE");
        absence.annuler("Erreur", MEDECIN, DroitDeCorrection.ACCORDE, false, AUCUNE_FACTURE, PLUS_TARD);

        assertThat(absence.statut()).isEqualTo(StatutAbsence.ANNULEE);
        assertThat(absence.motif()).isEqualTo(MotifAbsence.MALADIE);
    }

    @Test
    void annuler_deux_fois_ou_sans_commentaire_est_refuse() {
        AbsencePatient absence = aQualifier();

        assertRefus(() -> absence.annuler(" ", SECRETAIRE, DroitDeCorrection.REFUSE, false, AUCUNE_FACTURE, PLUS_TARD),
                "ABSENCE_COMMENTAIRE_REQUIS");
        assertRefus(() -> absence.annuler(null, SECRETAIRE, DroitDeCorrection.REFUSE, false, AUCUNE_FACTURE, PLUS_TARD),
                "ABSENCE_COMMENTAIRE_REQUIS");
        absence.annuler("Erreur", SECRETAIRE, DroitDeCorrection.REFUSE, false, AUCUNE_FACTURE, PLUS_TARD);
        assertRefus(() -> absence.annuler("Encore", MEDECIN, DroitDeCorrection.ACCORDE, false, AUCUNE_FACTURE, PLUS_TARD),
                "ABSENCE_DEJA_ANNULEE");
    }

    @Test
    void une_absence_rattrapee_ne_s_annule_pas_et_garde_sa_date_de_rattrapage() {
        AbsencePatient absence = rattrapee();

        assertThatThrownBy(() -> absence.annuler("Erreur", MEDECIN, DroitDeCorrection.ACCORDE, false, AUCUNE_FACTURE, PLUS_TARD))
                .isInstanceOf(AbsenceNonModifiable.class);

        assertThat(absence.statut()).isEqualTo(StatutAbsence.RATTRAPEE);
        assertThat(absence.dateDeRattrapage()).isEqualTo(SEANCE.plusDays(2));
    }

    @Test
    void annuler_dans_une_periode_facturee_est_refuse() {
        assertRefus(() -> aQualifier().annuler("Erreur", MEDECIN, DroitDeCorrection.ACCORDE, false, TOUT_FACTURE, PLUS_TARD),
                "ABSENCE_PERIODE_FACTUREE");
    }

    // --- retrait par la réinclusion de la séance

    @Test
    void la_reinclusion_retire_l_absence_de_l_exclusion_quel_que_soit_son_etat_sauf_annulee() {
        AbsencePatient requalifiee = justifiee();
        requalifiee.qualifier(MotifAbsence.VOYAGE, "Contrôle", MEDECIN, DroitDeCorrection.ACCORDE, AUCUNE_FACTURE, PLUS_TARD);

        aQualifier().exigerRetirableParLaReinclusion();
        justifiee().exigerRetirableParLaReinclusion();
        requalifiee.exigerRetirableParLaReinclusion();
        rattrapee().exigerRetirableParLaReinclusion();
    }

    @Test
    void une_absence_annulee_heritee_n_est_pas_retiree_par_la_reinclusion() {
        AbsencePatient annulee = aQualifier();
        annulee.annuler("Erreur", SECRETAIRE, DroitDeCorrection.REFUSE, false, AUCUNE_FACTURE, PLUS_TARD);

        assertRefus(annulee::exigerRetirableParLaReinclusion, "ABSENCE_D_EXCLUSION_MODIFIEE");
    }

    @Test
    void l_absence_issue_d_une_exclusion_ne_s_annule_pas_meme_avec_le_droit_de_correction() {
        AbsencePatient absence = justifiee();

        assertRefus(() -> absence.annuler("Erreur", MEDECIN, DroitDeCorrection.ACCORDE, true, AUCUNE_FACTURE, PLUS_TARD),
                "ABSENCE_D_EXCLUSION");
        assertThat(absence.statut()).isEqualTo(StatutAbsence.JUSTIFIEE);
        absence.annuler("Erreur", MEDECIN, DroitDeCorrection.ACCORDE, false, AUCUNE_FACTURE, PLUS_TARD);
        assertThat(absence.statut()).isEqualTo(StatutAbsence.ANNULEE);
    }

    // --- relecture

    @Test
    void relire_une_absence_enregistree_rejoue_aucune_regle_de_creation() {
        AbsencePatient lue = AbsencePatient.reconstituer(new AbsenceId(new UUID(5, 9)), CENTRE, PATIENT, AUJOURDHUI.minusDays(400),
                SourceAbsence.AUTOMATIQUE, VALEUR, null, INSTANT, StatutAbsence.JUSTIFIEE, MotifAbsence.DECES, "  ", null, null,
                null, null, null);

        assertThat(lue.dateDeSeance()).isEqualTo(AUJOURDHUI.minusDays(400));
        assertThat(lue.commentaire()).isNull();
        assertThat(lue.vue().statut()).isEqualTo(StatutAbsence.JUSTIFIEE);
        assertThat(lue.tirerLesEvenements()).isEmpty();
    }

    @Test
    void la_vue_reprend_les_champs_du_suivi_et_les_evenements_sont_tires_une_fois() {
        AbsencePatient absence = justifiee();

        AbsenceSuivie vue = absence.vue();

        assertThat(vue.id()).isEqualTo(absence.id());
        assertThat(vue.libelleDuForfait()).isEqualTo("Forfait HD");
        assertThat(vue.montantTtc()).isEqualByComparingTo("11900.00");
        assertThat(vue.declareeLe()).isEqualTo(INSTANT);
        assertThat(absence.tirerLesEvenements()).hasSize(2);
        assertThat(absence.tirerLesEvenements()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"id", "centre", "patient", "date", "source", "valeur", "statut"})
    void une_absence_reconstituee_sans_champ_obligatoire_est_refusee(String manquant) {
        assertThatThrownBy(() -> AbsencePatient.reconstituer(
                manquant.equals("id") ? null : new AbsenceId(new UUID(5, 9)),
                manquant.equals("centre") ? null : CENTRE, manquant.equals("patient") ? null : PATIENT,
                manquant.equals("date") ? null : SEANCE, manquant.equals("source") ? null : SourceAbsence.AUTOMATIQUE,
                manquant.equals("valeur") ? null : VALEUR, null, INSTANT,
                manquant.equals("statut") ? null : StatutAbsence.A_QUALIFIER, null, null, null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
