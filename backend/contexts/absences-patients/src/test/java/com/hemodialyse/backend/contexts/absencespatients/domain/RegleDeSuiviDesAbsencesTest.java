package com.hemodialyse.backend.contexts.absencespatients.domain;

import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.AbsenceId;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.PatientId;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Règles du domaine de la consultation du suivi (slice A1) : pagination, semaine du planning, délai de qualification,
 * séances de rattrapage, nom d'affichage du patient. Tests purs, sans double.
 */
class RegleDeSuiviDesAbsencesTest {

    private static final LocalDate AUJOURDHUI = LocalDate.of(2026, 10, 22);

    private static AbsenceSuivie absence(StatutAbsence statut, LocalDate date) {
        return new AbsenceSuivie(new AbsenceId(UUID.randomUUID()), new PatientId(UUID.randomUUID()), date,
                SourceAbsence.AUTOMATIQUE, statut, null, null, null, null, null, null, null, null, null);
    }

    // ---------------------------------------------------------------- pagination (KB-ABS-02)

    @Test
    void une_page_negative_est_refusee() {
        assertThrows(PaginationInvalide.class, () -> new Pagination(-1, 20));
    }

    @Test
    void une_taille_negative_est_refusee() {
        assertThrows(PaginationInvalide.class, () -> new Pagination(0, -1));
    }

    @Test
    void une_taille_au_dela_du_plafond_est_ramenee_au_plafond() {
        assertEquals(100, Pagination.TAILLE_MAXIMALE);
        assertEquals(100, new Pagination(0, 101).taille());
        assertEquals(100, new Pagination(0, 1000).taille());
        assertEquals(100, new Pagination(0, 100).taille());
        assertEquals(99, new Pagination(0, 99).taille());
    }

    @Test
    void une_taille_nulle_est_admise_et_la_premiere_page_a_un_decalage_nul() {
        assertEquals(0, new Pagination(3, 0).taille());
        assertEquals(0, new Pagination(3, 0).decalage());
        assertEquals(0, Pagination.premierePage().decalage());
        assertEquals(20, Pagination.premierePage().taille());
    }

    @Test
    void le_decalage_est_calcule_sans_depassement_d_entier() {
        assertEquals(2_147_483_647L * 100, new Pagination(Integer.MAX_VALUE, 100).decalage());
        assertEquals(40, new Pagination(2, 20).decalage());
    }

    // ---------------------------------------------------------------- semaine du planning

    @Test
    void la_semaine_va_du_dimanche_au_samedi() {
        SemaineDuPlanning semaine = SemaineDuPlanning.contenant(LocalDate.of(2026, 10, 22)); // jeudi

        assertEquals(LocalDate.of(2026, 10, 18), semaine.dimanche());
        assertEquals(DayOfWeek.SUNDAY, semaine.dimanche().getDayOfWeek());
        assertEquals(LocalDate.of(2026, 10, 24), semaine.samedi());
    }

    @Test
    void un_dimanche_ouvre_sa_propre_semaine_et_un_samedi_la_ferme() {
        assertEquals(LocalDate.of(2026, 10, 18), SemaineDuPlanning.contenant(LocalDate.of(2026, 10, 18)).dimanche());
        assertEquals(LocalDate.of(2026, 10, 18), SemaineDuPlanning.contenant(LocalDate.of(2026, 10, 24)).dimanche());
        assertEquals(LocalDate.of(2026, 10, 25), SemaineDuPlanning.contenant(LocalDate.of(2026, 10, 25)).dimanche());
    }

    @Test
    void une_semaine_ne_commence_pas_un_autre_jour_que_le_dimanche() {
        assertThrows(IllegalArgumentException.class, () -> new SemaineDuPlanning(LocalDate.of(2026, 10, 19)));
    }

    // ---------------------------------------------------------------- délai de qualification (RG-ABS-043)

    @Test
    void une_absence_a_qualifier_n_est_pas_en_retard_trois_jours_apres_et_l_est_quatre_jours_apres() {
        assertFalse(absence(StatutAbsence.A_QUALIFIER, AUJOURDHUI.minusDays(3)).enRetard(AUJOURDHUI));
        assertTrue(absence(StatutAbsence.A_QUALIFIER, AUJOURDHUI.minusDays(4)).enRetard(AUJOURDHUI));
        assertFalse(absence(StatutAbsence.A_QUALIFIER, AUJOURDHUI).enRetard(AUJOURDHUI));
        assertFalse(absence(StatutAbsence.A_QUALIFIER, AUJOURDHUI.plusDays(2)).enRetard(AUJOURDHUI));
    }

    @Test
    void seule_une_absence_a_qualifier_peut_etre_en_retard() {
        for (StatutAbsence statut : StatutAbsence.values()) {
            boolean attendu = statut == StatutAbsence.A_QUALIFIER;
            assertEquals(attendu, absence(statut, AUJOURDHUI.minusDays(30)).enRetard(AUJOURDHUI), statut.name());
        }
    }

    @Test
    void la_limite_du_retard_est_trois_jours_avant_aujourd_hui() {
        assertEquals(3, DelaiDeQualification.JOURS);
        assertEquals(LocalDate.of(2026, 10, 19), DelaiDeQualification.limite(AUJOURDHUI));
    }

    // ---------------------------------------------------------------- statuts (RG-ABS-003, 006)

    @Test
    void une_absence_est_comptabilisee_tant_qu_elle_n_est_ni_rattrapee_ni_annulee() {
        assertTrue(StatutAbsence.A_QUALIFIER.comptabilisee());
        assertTrue(StatutAbsence.JUSTIFIEE.comptabilisee());
        assertTrue(StatutAbsence.NON_JUSTIFIEE.comptabilisee());
        assertFalse(StatutAbsence.RATTRAPEE.comptabilisee());
        assertFalse(StatutAbsence.ANNULEE.comptabilisee());
    }

    @Test
    void une_absence_rattrapee_ou_annulee_n_est_plus_modifiable() {
        assertTrue(StatutAbsence.A_QUALIFIER.modifiable());
        assertTrue(StatutAbsence.JUSTIFIEE.modifiable());
        assertTrue(StatutAbsence.NON_JUSTIFIEE.modifiable());
        assertFalse(StatutAbsence.RATTRAPEE.modifiable());
        assertFalse(StatutAbsence.ANNULEE.modifiable());
    }

    @Test
    void seul_le_motif_non_justifie_ne_justifie_pas_et_seul_autre_exige_un_commentaire() {
        for (MotifAbsence motif : MotifAbsence.values()) {
            assertEquals(motif != MotifAbsence.NON_JUSTIFIEE, motif.justifiante(), motif.name());
            assertEquals(motif == MotifAbsence.AUTRE, motif.commentaireRequis(), motif.name());
        }
    }

    // ---------------------------------------------------------------- séances de rattrapage (RG-ABS-021, KB-ABS-04)

    @Test
    void une_absence_rattrapee_ou_annulee_n_est_pas_rattrapable() {
        assertThrows(AbsenceNonModifiable.class,
                () -> SeancesDeRattrapage.exigerRattrapable(absence(StatutAbsence.RATTRAPEE, AUJOURDHUI)));
        assertThrows(AbsenceNonModifiable.class,
                () -> SeancesDeRattrapage.exigerRattrapable(absence(StatutAbsence.ANNULEE, AUJOURDHUI)));
    }

    @Test
    void une_absence_a_qualifier_justifiee_ou_non_justifiee_est_rattrapable() {
        for (StatutAbsence statut : List.of(StatutAbsence.A_QUALIFIER, StatutAbsence.JUSTIFIEE, StatutAbsence.NON_JUSTIFIEE)) {
            SeancesDeRattrapage.exigerRattrapable(absence(statut, AUJOURDHUI));
        }
    }

    @Test
    void la_premiere_seance_possible_est_le_lendemain_de_la_seance_manquee() {
        assertEquals(LocalDate.of(2026, 10, 11),
                SeancesDeRattrapage.premierJourPossible(absence(StatutAbsence.A_QUALIFIER, LocalDate.of(2026, 10, 10))));
    }

    @Test
    void les_seances_deja_utilisees_pour_une_autre_absence_ne_sont_pas_proposees_et_l_ordre_est_conserve() {
        LocalDate d1 = LocalDate.of(2026, 10, 11);
        LocalDate d2 = LocalDate.of(2026, 10, 12);
        LocalDate d3 = LocalDate.of(2026, 10, 13);

        assertEquals(List.of(d1, d3), SeancesDeRattrapage.proposees(List.of(d1, d2, d3), List.of(d2)));
        assertEquals(List.of(d1, d2, d3), SeancesDeRattrapage.proposees(List.of(d1, d2, d3), List.of()));
        assertEquals(List.of(), SeancesDeRattrapage.proposees(List.of(), List.of(d1)));
    }

    // ---------------------------------------------------------------- nom d'affichage (KB-ABS-09) et identités

    @Test
    void le_nom_du_patient_est_prenom_nom_et_un_prenom_blanc_ou_absent_est_ignore() {
        assertEquals("Karim Amrani", NomDuPatient.complet("Karim", "Amrani"));
        assertEquals("Amrani", NomDuPatient.complet("  ", "Amrani"));
        assertEquals("Amrani", NomDuPatient.complet(null, "Amrani"));
        assertNull(NomDuPatient.complet("Karim", null));
        assertNull(NomDuPatient.complet(null, null));
    }

    @Test
    void les_identifiants_exigent_une_valeur_et_une_absence_suivie_une_identite_complete() {
        assertThrows(IllegalArgumentException.class, () -> new Identifiants.CentreId(null));
        assertThrows(IllegalArgumentException.class, () -> new PatientId(null));
        assertThrows(IllegalArgumentException.class, () -> new AbsenceId(null));
        assertThrows(IllegalArgumentException.class, () -> new AbsenceSuivie(null, new PatientId(UUID.randomUUID()),
                AUJOURDHUI, SourceAbsence.DECLAREE, StatutAbsence.A_QUALIFIER, null, null, null, null, null, null, null,
                null, null));
    }

    @Test
    void sans_filtre_aucun_critere_n_est_pose() {
        FiltreDesAbsences aucun = FiltreDesAbsences.aucun();

        assertNull(aucun.statut());
        assertNull(aucun.motif());
        assertNull(aucun.patient());
        assertNull(aucun.du());
        assertNull(aucun.au());
    }
}
