package com.hemodialyse.backend.contexts.absencespatients.application;

import com.hemodialyse.backend.contexts.absencespatients.doubles.SuiviEnMemoire;
import com.hemodialyse.backend.contexts.absencespatients.domain.AbsenceIntrouvable;
import com.hemodialyse.backend.contexts.absencespatients.domain.AbsenceNonModifiable;
import com.hemodialyse.backend.contexts.absencespatients.domain.AbsenceSuivie;
import com.hemodialyse.backend.contexts.absencespatients.domain.FiltreDesAbsences;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.AbsenceId;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.PatientId;
import com.hemodialyse.backend.contexts.absencespatients.domain.MotifAbsence;
import com.hemodialyse.backend.contexts.absencespatients.domain.Pagination;
import com.hemodialyse.backend.contexts.absencespatients.domain.SeanceRealisee;
import com.hemodialyse.backend.contexts.absencespatients.domain.SourceAbsence;
import com.hemodialyse.backend.contexts.absencespatients.domain.StatutAbsence;
import com.hemodialyse.backend.contexts.absencespatients.domain.SyntheseDesAbsences;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Les quatre cas d'usage de consultation du suivi (slice A1), avec le double en mémoire des ports et une horloge figée au
 * jeudi 22 octobre 2026 (UTC).
 */
class ConsultationDuSuiviTest {

    private static final LocalDate AUJOURDHUI = LocalDate.of(2026, 10, 22);
    private static final HorlogeDesCentres HORLOGE = HorlogeDesCentres.enUtc(
            Clock.fixed(Instant.parse("2026-10-22T10:00:00Z"), ZoneOffset.UTC));
    private static final CentreId CENTRE = new CentreId(new UUID(1, 1));
    private static final CentreId AUTRE_CENTRE = new CentreId(new UUID(1, 2));
    private static final PatientId AMRANI = new PatientId(new UUID(2, 1));
    private static final PatientId BELKACEM = new PatientId(new UUID(2, 2));

    private final SuiviEnMemoire memoire = new SuiviEnMemoire();

    private static AbsenceSuivie absence(int rang, PatientId patient, LocalDate date, StatutAbsence statut, LocalDate rattrapage) {
        return new AbsenceSuivie(new AbsenceId(new UUID(3, rang)), patient, date, SourceAbsence.AUTOMATIQUE, statut,
                statut == StatutAbsence.JUSTIFIEE ? MotifAbsence.MALADIE : null, null, null, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, null, null, rattrapage);
    }

    private void absenceDuCentre(int rang, PatientId patient, LocalDate date, StatutAbsence statut) {
        memoire.poserLAbsence(CENTRE, absence(rang, patient, date, statut, null));
    }

    // ---------------------------------------------------------------- liste

    @Test
    void la_liste_indique_le_retard_au_jour_courant_pour_les_seules_absences_a_qualifier() {
        absenceDuCentre(1, AMRANI, AUJOURDHUI.minusDays(3), StatutAbsence.A_QUALIFIER);
        absenceDuCentre(2, BELKACEM, AUJOURDHUI.minusDays(4), StatutAbsence.A_QUALIFIER);
        absenceDuCentre(3, AMRANI, AUJOURDHUI.minusDays(10), StatutAbsence.JUSTIFIEE);
        var lister = new ListerLesAbsences(memoire, memoire, HORLOGE);

        var page = lister.executer(CENTRE, FiltreDesAbsences.aucun(), Pagination.premierePage());

        assertThat(page.elements()).extracting(l -> l.absence().id().valeur().getLeastSignificantBits(), LigneDuSuivi::enRetard)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(1L, false), org.assertj.core.groups.Tuple.tuple(2L, true),
                        org.assertj.core.groups.Tuple.tuple(3L, false));
        assertThat(page.total()).isEqualTo(3);
    }

    @Test
    void la_liste_ne_montre_que_les_absences_du_centre_avec_le_nom_du_patient() {
        memoire.poserLePatient(CENTRE, AMRANI, "Karim", "Amrani");
        absenceDuCentre(1, AMRANI, AUJOURDHUI, StatutAbsence.A_QUALIFIER);
        memoire.poserLAbsence(AUTRE_CENTRE, absence(2, BELKACEM, AUJOURDHUI, StatutAbsence.A_QUALIFIER, null));
        var lister = new ListerLesAbsences(memoire, memoire, HORLOGE);

        var page = lister.executer(CENTRE, FiltreDesAbsences.aucun(), Pagination.premierePage());

        assertThat(page.elements()).hasSize(1);
        assertThat(page.elements().get(0).nomDuPatient()).isEqualTo("Karim Amrani");
    }

    @Test
    void la_taille_demandee_est_plafonnee_et_la_taille_effective_est_renvoyee() {
        for (int rang = 1; rang <= 3; rang++) {
            absenceDuCentre(rang, AMRANI, AUJOURDHUI.minusDays(rang), StatutAbsence.A_QUALIFIER);
        }
        var lister = new ListerLesAbsences(memoire, memoire, HORLOGE);

        var page = lister.executer(CENTRE, FiltreDesAbsences.aucun(), new Pagination(0, 5000));

        assertThat(page.taille()).isEqualTo(Pagination.TAILLE_MAXIMALE);
        assertThat(page.elements()).hasSize(3);
    }

    // ---------------------------------------------------------------- semaine

    @Test
    void sans_jour_demande_le_suivi_est_celui_de_la_semaine_courante_dimanche_au_samedi() {
        // jeudi 22/10/2026 : semaine du dimanche 18 au samedi 24
        absenceDuCentre(1, AMRANI, LocalDate.of(2026, 10, 18), StatutAbsence.A_QUALIFIER);
        absenceDuCentre(2, BELKACEM, LocalDate.of(2026, 10, 24), StatutAbsence.JUSTIFIEE);
        absenceDuCentre(3, AMRANI, LocalDate.of(2026, 10, 17), StatutAbsence.A_QUALIFIER);
        absenceDuCentre(4, AMRANI, LocalDate.of(2026, 10, 25), StatutAbsence.A_QUALIFIER);
        absenceDuCentre(5, AMRANI, LocalDate.of(2026, 10, 20), StatutAbsence.ANNULEE);
        memoire.poserLaSeance(CENTRE, AMRANI, LocalDate.of(2026, 10, 19), "VALIDEE");
        memoire.poserLaSeance(CENTRE, AMRANI, LocalDate.of(2026, 10, 25), "VALIDEE");
        var suivre = new SuivreLaSemaine(memoire, memoire, HORLOGE);

        var suivi = suivre.executer(CENTRE, null);

        assertThat(suivi.absences()).extracting(a -> a.id().valeur().getLeastSignificantBits()).containsExactly(1L, 2L);
        assertThat(suivi.seancesRealisees()).containsExactly(new SeanceRealisee(AMRANI, LocalDate.of(2026, 10, 19)));
    }

    @Test
    void un_jour_demande_designe_la_semaine_qui_le_contient() {
        absenceDuCentre(1, AMRANI, LocalDate.of(2026, 10, 12), StatutAbsence.A_QUALIFIER);
        absenceDuCentre(2, BELKACEM, LocalDate.of(2026, 10, 18), StatutAbsence.A_QUALIFIER);
        var suivre = new SuivreLaSemaine(memoire, memoire, HORLOGE);

        var suivi = suivre.executer(CENTRE, LocalDate.of(2026, 10, 14));

        assertThat(suivi.absences()).extracting(a -> a.id().valeur().getLeastSignificantBits()).containsExactly(1L);
    }

    @Test
    void le_suivi_de_la_semaine_est_borne_au_centre() {
        memoire.poserLAbsence(AUTRE_CENTRE, absence(1, AMRANI, AUJOURDHUI, StatutAbsence.A_QUALIFIER, null));
        memoire.poserLaSeance(AUTRE_CENTRE, AMRANI, AUJOURDHUI, "VALIDEE");

        var suivi = new SuivreLaSemaine(memoire, memoire, HORLOGE).executer(CENTRE, null);

        assertThat(suivi.absences()).isEmpty();
        assertThat(suivi.seancesRealisees()).isEmpty();
    }

    // ---------------------------------------------------------------- synthèse

    @Test
    void la_synthese_compte_les_absences_a_qualifier_et_celles_qui_sont_en_retard() {
        absenceDuCentre(1, AMRANI, AUJOURDHUI.minusDays(1), StatutAbsence.A_QUALIFIER);
        absenceDuCentre(2, BELKACEM, AUJOURDHUI.minusDays(3), StatutAbsence.A_QUALIFIER);
        absenceDuCentre(3, AMRANI, AUJOURDHUI.minusDays(4), StatutAbsence.A_QUALIFIER);
        absenceDuCentre(4, BELKACEM, AUJOURDHUI.minusDays(30), StatutAbsence.JUSTIFIEE);
        memoire.poserLAbsence(AUTRE_CENTRE, absence(5, AMRANI, AUJOURDHUI.minusDays(30), StatutAbsence.A_QUALIFIER, null));

        var synthese = new ObtenirLaSynthese(memoire, HORLOGE).executer(CENTRE);

        assertThat(synthese).isEqualTo(new SyntheseDesAbsences(3, 1));
    }

    // ---------------------------------------------------------------- séances de rattrapage

    private ListerLesSeancesDeRattrapage rattrapage() {
        return new ListerLesSeancesDeRattrapage(memoire, memoire, HORLOGE);
    }

    @Test
    void les_seances_proposees_suivent_la_seance_manquee_jusqu_a_aujourd_hui_sans_celles_deja_utilisees() {
        LocalDate manquee = AUJOURDHUI.minusDays(10);
        absenceDuCentre(1, AMRANI, manquee, StatutAbsence.A_QUALIFIER);
        memoire.poserLAbsence(CENTRE, absence(2, AMRANI, AUJOURDHUI.minusDays(20), StatutAbsence.RATTRAPEE, AUJOURDHUI.minusDays(2)));
        memoire.poserLaSeance(CENTRE, AMRANI, manquee, "VALIDEE");                       // le jour même : exclue
        memoire.poserLaSeance(CENTRE, AMRANI, manquee.plusDays(1), "VALIDEE");           // lendemain : proposée
        memoire.poserLaSeance(CENTRE, AMRANI, manquee.plusDays(2), "CREE");              // non réalisée
        memoire.poserLaSeance(CENTRE, AMRANI, AUJOURDHUI.minusDays(2), "SIGNEE");        // utilisée par l'absence 2
        memoire.poserLaSeance(CENTRE, AMRANI, AUJOURDHUI, "FACTUREE");                   // aujourd'hui : proposée
        memoire.poserLaSeance(CENTRE, AMRANI, AUJOURDHUI.plusDays(1), "VALIDEE");        // futur
        memoire.poserLaSeance(CENTRE, BELKACEM, manquee.plusDays(3), "VALIDEE");         // autre patient

        var dates = rattrapage().executer(CENTRE, new AbsenceId(new UUID(3, 1)));

        assertThat(dates).containsExactly(manquee.plusDays(1), AUJOURDHUI);
    }

    @Test
    void une_absence_justifiee_ou_non_justifiee_est_rattrapable() {
        absenceDuCentre(1, AMRANI, AUJOURDHUI.minusDays(5), StatutAbsence.JUSTIFIEE);
        absenceDuCentre(2, BELKACEM, AUJOURDHUI.minusDays(5), StatutAbsence.NON_JUSTIFIEE);
        memoire.poserLaSeance(CENTRE, AMRANI, AUJOURDHUI.minusDays(1), "VALIDEE");

        assertThat(rattrapage().executer(CENTRE, new AbsenceId(new UUID(3, 1)))).containsExactly(AUJOURDHUI.minusDays(1));
        assertThat(rattrapage().executer(CENTRE, new AbsenceId(new UUID(3, 2)))).isEmpty();
    }

    @Test
    void aucune_seance_n_est_proposee_pour_une_absence_rattrapee_ou_annulee() {
        memoire.poserLAbsence(CENTRE, absence(1, AMRANI, AUJOURDHUI.minusDays(5), StatutAbsence.RATTRAPEE, AUJOURDHUI.minusDays(1)));
        absenceDuCentre(2, BELKACEM, AUJOURDHUI.minusDays(5), StatutAbsence.ANNULEE);
        memoire.poserLaSeance(CENTRE, AMRANI, AUJOURDHUI.minusDays(2), "VALIDEE");

        assertThrows(AbsenceNonModifiable.class, () -> rattrapage().executer(CENTRE, new AbsenceId(new UUID(3, 1))));
        assertThrows(AbsenceNonModifiable.class, () -> rattrapage().executer(CENTRE, new AbsenceId(new UUID(3, 2))));
    }

    @Test
    void une_absence_inconnue_ou_d_un_autre_centre_est_introuvable() {
        memoire.poserLAbsence(AUTRE_CENTRE, absence(1, AMRANI, AUJOURDHUI.minusDays(5), StatutAbsence.A_QUALIFIER, null));

        AbsenceIntrouvable autreCentre = assertThrows(AbsenceIntrouvable.class,
                () -> rattrapage().executer(CENTRE, new AbsenceId(new UUID(3, 1))));
        assertThat(autreCentre.absence()).isEqualTo(new AbsenceId(new UUID(3, 1)));
        assertThrows(AbsenceIntrouvable.class, () -> rattrapage().executer(CENTRE, new AbsenceId(new UUID(3, 99))));
        assertThat(rattrapage().executer(AUTRE_CENTRE, new AbsenceId(new UUID(3, 1)))).isEmpty();
    }

    @Test
    void une_absence_du_jour_n_a_aucune_seance_possible_avant_demain() {
        absenceDuCentre(1, AMRANI, AUJOURDHUI, StatutAbsence.A_QUALIFIER);
        memoire.poserLaSeance(CENTRE, AMRANI, AUJOURDHUI, "VALIDEE");

        assertThat(rattrapage().executer(CENTRE, new AbsenceId(new UUID(3, 1)))).isEqualTo(List.of());
    }
}
