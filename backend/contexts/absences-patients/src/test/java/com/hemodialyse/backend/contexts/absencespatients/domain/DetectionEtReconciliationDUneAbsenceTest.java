package com.hemodialyse.backend.contexts.absencespatients.domain;

import com.hemodialyse.backend.contexts.absencespatients.domain.EvenementDAbsence.AbsenceDetectee;
import com.hemodialyse.backend.contexts.absencespatients.domain.EvenementDAbsence.AbsenceReconciliee;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.AbsenceId;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.ForfaitId;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.PatientId;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.UtilisateurId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Détection et réconciliation d'une absence (matrice A12, A13) : domaine pur.
 */
class DetectionEtReconciliationDUneAbsenceTest {

    private static final CentreId CENTRE = new CentreId(new UUID(1, 1));
    private static final PatientId PATIENT = new PatientId(new UUID(2, 1));
    private static final UtilisateurId SECRETAIRE = new UtilisateurId(new UUID(3, 1));
    private static final AbsenceId ID = new AbsenceId(new UUID(5, 1));
    private static final LocalDate SEANCE = LocalDate.of(2026, 10, 17);
    private static final Instant NUIT = Instant.parse("2026-10-22T01:30:00Z");
    private static final Instant PLUS_TARD = Instant.parse("2026-10-22T02:30:00Z");
    private static final PeriodesFacturees AUCUNE_FACTURE = (centre, patient, jour) -> false;
    private static final PeriodesFacturees TOUT_FACTURE = (centre, patient, jour) -> true;
    private static final MoisDAbsencesOuverts OUVERT = mois(true);
    private static final MoisDAbsencesOuverts CLOS = mois(false);
    private static final Valorisation VALEUR = Valorisation.depuis(
            new ForfaitApplicable(new ForfaitId(new UUID(4, 1)), "Forfait HD", new BigDecimal("10000.00")), new BigDecimal("19"));

    private static MoisDAbsencesOuverts mois(boolean ouvert) {
        return new MoisDAbsencesOuverts() {
            @Override
            public boolean estOuvert(CentreId centre, java.time.YearMonth mois) {
                return ouvert;
            }

            @Override
            public java.util.List<java.time.YearMonth> moisClos(CentreId centre) {
                return ouvert ? java.util.List.of() : java.util.List.of(java.time.YearMonth.from(SEANCE));
            }
        };
    }

    private static DateOuverte ouverte() {
        return new DateOuverte(CENTRE, PATIENT, SEANCE);
    }

    private static AbsencePatient detectee() {
        return AbsencePatient.detecter(ID, ouverte(), VALEUR, NUIT);
    }

    @Test
    void une_absence_detectee_est_a_qualifier_de_source_automatique_et_sans_auteur() {
        AbsencePatient absence = detectee();

        assertThat(absence.statut()).isEqualTo(StatutAbsence.A_QUALIFIER);
        assertThat(absence.source()).isEqualTo(SourceAbsence.AUTOMATIQUE);
        assertThat(absence.declareePar()).isNull();
        assertThat(absence.declareeLe()).isEqualTo(NUIT);
        assertThat(absence.dateDeSeance()).isEqualTo(SEANCE);
        assertThat(absence.motif()).isNull();
        assertThat(absence.valorisation()).isEqualTo(VALEUR);
        assertThat(absence.tirerLesEvenements()).containsExactly(new AbsenceDetectee(ID, CENTRE, PATIENT, SEANCE, null, NUIT));
    }

    @Test
    void une_date_facturee_ou_un_mois_clos_n_ouvre_pas_la_detection() {
        assertThat(DateOuverte.siOuverte(AUCUNE_FACTURE, OUVERT, CENTRE, PATIENT, SEANCE)).contains(ouverte());
        assertThat(DateOuverte.siOuverte(TOUT_FACTURE, OUVERT, CENTRE, PATIENT, SEANCE)).isEmpty();
        assertThat(DateOuverte.siOuverte(AUCUNE_FACTURE, CLOS, CENTRE, PATIENT, SEANCE)).isEmpty();
    }

    @Test
    void la_reconciliation_annule_une_absence_detectee_avec_le_commentaire_fixe_et_sans_auteur() {
        AbsencePatient absence = detectee();
        absence.tirerLesEvenements();

        absence.reconcilier(AUCUNE_FACTURE, PLUS_TARD);

        assertThat(absence.statut()).isEqualTo(StatutAbsence.ANNULEE);
        assertThat(absence.commentaire()).isEqualTo("Séance réalisée à cette date");
        assertThat(absence.modifieePar()).isNull();
        assertThat(absence.modifieeLe()).isEqualTo(PLUS_TARD);
        assertThat(absence.tirerLesEvenements()).containsExactly(new AbsenceReconciliee(ID, CENTRE, PATIENT, SEANCE, null, PLUS_TARD));
    }

    @Test
    void une_absence_declaree_par_un_utilisateur_ne_se_reconcilie_pas() {
        AbsencePatient declaree = AbsencePatient.declarer(ID, ouverte(), null, null, VALEUR, SECRETAIRE, SEANCE.plusDays(1), NUIT);

        assertThat(declaree.reconciliable(AUCUNE_FACTURE)).isFalse();
        assertThatThrownBy(() -> declaree.reconcilier(AUCUNE_FACTURE, PLUS_TARD)).isInstanceOfSatisfying(RegleDAbsenceViolee.class,
                e -> assertThat(e.code()).isEqualTo("ABSENCE_NON_RECONCILIABLE"));
        assertThat(declaree.statut()).isEqualTo(StatutAbsence.A_QUALIFIER);
    }

    @Test
    void une_absence_en_periode_facturee_ne_se_reconcilie_pas() {
        AbsencePatient absence = detectee();

        assertThat(absence.reconciliable(TOUT_FACTURE)).isFalse();
        assertThatThrownBy(() -> absence.reconcilier(TOUT_FACTURE, PLUS_TARD)).isInstanceOf(RegleDAbsenceViolee.class);
        assertThat(absence.statut()).isEqualTo(StatutAbsence.A_QUALIFIER);
    }

    @Test
    void une_absence_deja_qualifiee_ou_annulee_ne_se_reconcilie_pas() {
        AbsencePatient qualifiee = AbsencePatient.reconstituer(ID, CENTRE, PATIENT, SEANCE, SourceAbsence.AUTOMATIQUE, VALEUR, null,
                NUIT, StatutAbsence.JUSTIFIEE, MotifAbsence.MALADIE, null, SECRETAIRE, NUIT, null, null, null);
        AbsencePatient annulee = AbsencePatient.reconstituer(ID, CENTRE, PATIENT, SEANCE, SourceAbsence.AUTOMATIQUE, VALEUR, null,
                NUIT, StatutAbsence.ANNULEE, null, "x", null, null, null, null, null);

        assertThat(qualifiee.reconciliable(AUCUNE_FACTURE)).isFalse();
        assertThat(annulee.reconciliable(AUCUNE_FACTURE)).isFalse();
    }
}
