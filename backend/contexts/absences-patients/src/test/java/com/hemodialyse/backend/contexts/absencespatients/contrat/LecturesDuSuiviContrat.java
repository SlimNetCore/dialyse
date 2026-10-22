package com.hemodialyse.backend.contexts.absencespatients.contrat;

import com.hemodialyse.backend.contexts.absencespatients.application.LectureDesAbsences;
import com.hemodialyse.backend.contexts.absencespatients.application.SeancesRealisees;
import com.hemodialyse.backend.contexts.absencespatients.domain.AbsenceSuivie;
import com.hemodialyse.backend.contexts.absencespatients.domain.FiltreDesAbsences;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.AbsenceId;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.PatientId;
import com.hemodialyse.backend.contexts.absencespatients.domain.MotifAbsence;
import com.hemodialyse.backend.contexts.absencespatients.domain.PageDeResultats;
import com.hemodialyse.backend.contexts.absencespatients.domain.Pagination;
import com.hemodialyse.backend.contexts.absencespatients.domain.SeanceRealisee;
import com.hemodialyse.backend.contexts.absencespatients.domain.SourceAbsence;
import com.hemodialyse.backend.contexts.absencespatients.domain.StatutAbsence;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrat des lectures du suivi : le même jeu de tests passe contre le double en mémoire, les adaptateurs JDBC sur H2 et
 * sur PostgreSQL 16 (le double est donc fidèle). Les identifiants croissent avec le rang, pour que le tri secondaire par
 * identifiant soit le même quelle que soit la base.
 */
public abstract class LecturesDuSuiviContrat {

    protected static final CentreId CENTRE = new CentreId(new UUID(0x7000, 0xa));
    protected static final CentreId AUTRE_CENTRE = new CentreId(new UUID(0x7000, 0xb));
    protected static final PatientId AMRANI = new PatientId(new UUID(0x7001, 1));
    protected static final PatientId BELKACEM = new PatientId(new UUID(0x7001, 2));
    protected static final PatientId ETRANGER = new PatientId(new UUID(0x7001, 3));
    private static final LocalDate JOUR = LocalDate.of(2026, 3, 10);

    protected abstract LectureDesAbsences lecture();

    protected abstract SeancesRealisees seances();

    protected abstract com.hemodialyse.backend.contexts.absencespatients.application.NomsDesPatients noms();

    protected abstract void poserLePatient(CentreId centre, PatientId id, String prenom, String nom);

    protected abstract void poserLAbsence(CentreId centre, AbsenceSuivie absence);

    protected abstract void poserLaSeance(CentreId centre, PatientId patient, LocalDate date, String statut);

    protected static AbsenceId id(int rang) {
        return new AbsenceId(new UUID(0x7002, rang));
    }

    protected static AbsenceSuivie absence(int rang, PatientId patient, LocalDate date, StatutAbsence statut) {
        return new AbsenceSuivie(id(rang), patient, date, SourceAbsence.AUTOMATIQUE, statut,
                statut == StatutAbsence.A_QUALIFIER || statut == StatutAbsence.ANNULEE ? null : MotifAbsence.MALADIE,
                null, "Forfait", new BigDecimal("11900.00"), new BigDecimal("19.00"), new BigDecimal("10000.00"),
                Instant.parse("2026-03-10T08:00:00Z"), null, null);
    }

    private static AbsenceSuivie rattrapee(int rang, PatientId patient, LocalDate date, LocalDate rattrapage) {
        AbsenceSuivie base = absence(rang, patient, date, StatutAbsence.RATTRAPEE);
        return new AbsenceSuivie(base.id(), base.patient(), base.dateDeSeance(), base.source(), base.statut(), base.motif(),
                base.commentaire(), base.libelleDuForfait(), base.montantTtc(), base.tauxDeTva(), base.montantHt(),
                base.declareeLe(), base.qualifieeLe(), rattrapage);
    }

    private void lesPatients() {
        poserLePatient(CENTRE, AMRANI, "Karim", "Amrani");
        poserLePatient(CENTRE, BELKACEM, " ", "Belkacem");
        poserLePatient(AUTRE_CENTRE, ETRANGER, "Yacine", "Etranger");
    }

    private static List<Integer> rangs(PageDeResultats<AbsenceSuivie> page) {
        return page.elements().stream().map(l -> (int) l.id().valeur().getLeastSignificantBits()).toList();
    }

    @Test
    void la_liste_est_triee_par_date_decroissante_puis_identifiant_et_porte_le_nom_du_patient() {
        lesPatients();
        poserLAbsence(CENTRE, absence(1, AMRANI, JOUR, StatutAbsence.A_QUALIFIER));
        poserLAbsence(CENTRE, absence(2, BELKACEM, JOUR, StatutAbsence.JUSTIFIEE));
        poserLAbsence(CENTRE, absence(3, AMRANI, JOUR.plusDays(2), StatutAbsence.ANNULEE));
        poserLAbsence(CENTRE, absence(4, AMRANI, JOUR.minusDays(1), StatutAbsence.NON_JUSTIFIEE));

        var page = lecture().lister(CENTRE, FiltreDesAbsences.aucun(), Pagination.premierePage());

        assertThat(rangs(page)).containsExactly(3, 1, 2, 4);
        assertThat(page.total()).isEqualTo(4);
        assertThat(page.page()).isZero();
        assertThat(page.taille()).isEqualTo(20);
        AbsenceSuivie premiere = page.elements().get(1);
        assertThat(premiere.montantTtc()).isEqualByComparingTo("11900.00");
        assertThat(premiere.tauxDeTva()).isEqualByComparingTo("19.00");
        assertThat(premiere.montantHt()).isEqualByComparingTo("10000.00");
        assertThat(premiere.libelleDuForfait()).isEqualTo("Forfait");
        assertThat(premiere.source()).isEqualTo(SourceAbsence.AUTOMATIQUE);
        assertThat(premiere.declareeLe()).isEqualTo(Instant.parse("2026-03-10T08:00:00Z"));
        assertThat(premiere.motif()).isNull();
    }

    @Test
    void le_nom_du_patient_est_prenom_nom_avec_un_prenom_blanc_ignore() {
        lesPatients();

        var noms = noms().parPatient(CENTRE, List.of(AMRANI, BELKACEM));

        assertThat(noms).containsEntry(AMRANI, "Karim Amrani").containsEntry(BELKACEM, "Belkacem").hasSize(2);
    }

    @Test
    void un_patient_inconnu_ou_d_un_autre_centre_n_a_pas_de_nom_et_une_demande_vide_ne_donne_rien() {
        lesPatients();

        var noms = noms().parPatient(CENTRE, List.of(ETRANGER, new PatientId(new UUID(0x7001, 99)), AMRANI, AMRANI));

        assertThat(noms).containsOnlyKeys(AMRANI);
        assertThat(noms().parPatient(CENTRE, List.of())).isEmpty();
    }

    @Test
    void la_liste_est_bornee_au_centre() {
        lesPatients();
        poserLAbsence(CENTRE, absence(1, AMRANI, JOUR, StatutAbsence.A_QUALIFIER));
        poserLAbsence(AUTRE_CENTRE, absence(2, ETRANGER, JOUR, StatutAbsence.A_QUALIFIER));

        assertThat(rangs(lecture().lister(CENTRE, FiltreDesAbsences.aucun(), Pagination.premierePage()))).containsExactly(1);
        assertThat(rangs(lecture().lister(AUTRE_CENTRE, FiltreDesAbsences.aucun(), Pagination.premierePage()))).containsExactly(2);
        assertThat(lecture().lister(new CentreId(new UUID(0x7000, 0xc)), FiltreDesAbsences.aucun(), Pagination.premierePage())
                .total()).isZero();
    }

    @Test
    void les_filtres_statut_motif_patient_et_bornes_de_dates_se_combinent() {
        lesPatients();
        poserLAbsence(CENTRE, absence(1, AMRANI, JOUR, StatutAbsence.A_QUALIFIER));
        poserLAbsence(CENTRE, absence(2, BELKACEM, JOUR.plusDays(1), StatutAbsence.JUSTIFIEE));
        poserLAbsence(CENTRE, absence(3, AMRANI, JOUR.plusDays(2), StatutAbsence.JUSTIFIEE));
        poserLAbsence(CENTRE, absence(4, AMRANI, JOUR.plusDays(3), StatutAbsence.NON_JUSTIFIEE));
        Pagination toutes = Pagination.premierePage();

        assertThat(rangs(lecture().lister(CENTRE,
                new FiltreDesAbsences(StatutAbsence.JUSTIFIEE, null, null, null, null), toutes))).containsExactly(3, 2);
        assertThat(rangs(lecture().lister(CENTRE,
                new FiltreDesAbsences(null, MotifAbsence.MALADIE, null, null, null), toutes))).containsExactly(4, 3, 2);
        assertThat(rangs(lecture().lister(CENTRE,
                new FiltreDesAbsences(null, null, AMRANI, null, null), toutes))).containsExactly(4, 3, 1);
        assertThat(rangs(lecture().lister(CENTRE,
                new FiltreDesAbsences(null, null, null, JOUR.plusDays(1), JOUR.plusDays(2)), toutes)))
                .as("bornes inclusives").containsExactly(3, 2);
        assertThat(rangs(lecture().lister(CENTRE,
                new FiltreDesAbsences(StatutAbsence.JUSTIFIEE, MotifAbsence.MALADIE, AMRANI, JOUR, JOUR.plusDays(3)), toutes)))
                .containsExactly(3);
        assertThat(lecture().lister(CENTRE, new FiltreDesAbsences(null, null, null, JOUR.plusDays(2), JOUR), toutes).total())
                .as("bornes incohérentes : rien").isZero();
    }

    @Test
    void la_pagination_decoupe_le_resultat_et_une_taille_nulle_renseigne_le_total() {
        lesPatients();
        for (int rang = 1; rang <= 5; rang++) {
            poserLAbsence(CENTRE, absence(rang, AMRANI, JOUR.plusDays(rang), StatutAbsence.A_QUALIFIER));
        }
        FiltreDesAbsences aucun = FiltreDesAbsences.aucun();

        assertThat(rangs(lecture().lister(CENTRE, aucun, new Pagination(0, 2)))).containsExactly(5, 4);
        assertThat(rangs(lecture().lister(CENTRE, aucun, new Pagination(1, 2)))).containsExactly(3, 2);
        assertThat(rangs(lecture().lister(CENTRE, aucun, new Pagination(2, 2)))).containsExactly(1);
        assertThat(lecture().lister(CENTRE, aucun, new Pagination(3, 2)).elements()).as("au-delà").isEmpty();
        assertThat(lecture().lister(CENTRE, aucun, new Pagination(3, 2)).total()).isEqualTo(5);
        var vide = lecture().lister(CENTRE, aucun, new Pagination(4, 0));
        assertThat(vide.elements()).isEmpty();
        assertThat(vide.total()).isEqualTo(5);
        assertThat(vide.taille()).isZero();
    }

    @Test
    void la_semaine_exclut_les_annulees_inclut_les_bornes_et_trie_par_date_puis_identifiant() {
        lesPatients();
        poserLAbsence(CENTRE, absence(1, AMRANI, JOUR, StatutAbsence.A_QUALIFIER));
        poserLAbsence(CENTRE, absence(2, BELKACEM, JOUR, StatutAbsence.RATTRAPEE));
        poserLAbsence(CENTRE, absence(3, AMRANI, JOUR.minusDays(1), StatutAbsence.JUSTIFIEE));
        poserLAbsence(CENTRE, absence(4, AMRANI, JOUR.plusDays(1), StatutAbsence.ANNULEE));
        poserLAbsence(CENTRE, absence(5, AMRANI, JOUR.minusDays(2), StatutAbsence.JUSTIFIEE));
        poserLAbsence(CENTRE, absence(6, AMRANI, JOUR.plusDays(2), StatutAbsence.JUSTIFIEE));
        poserLAbsence(AUTRE_CENTRE, absence(7, ETRANGER, JOUR, StatutAbsence.A_QUALIFIER));

        var absences = lecture().absencesNonAnnuleesEntre(CENTRE, JOUR.minusDays(1), JOUR.plusDays(1));

        assertThat(absences).extracting(a -> (int) a.id().valeur().getLeastSignificantBits()).containsExactly(3, 1, 2);
    }

    @Test
    void les_comptes_a_qualifier_et_avant_une_limite_stricte_sont_bornes_au_centre() {
        lesPatients();
        poserLAbsence(CENTRE, absence(1, AMRANI, JOUR, StatutAbsence.A_QUALIFIER));
        poserLAbsence(CENTRE, absence(2, BELKACEM, JOUR.minusDays(1), StatutAbsence.A_QUALIFIER));
        poserLAbsence(CENTRE, absence(3, AMRANI, JOUR.minusDays(5), StatutAbsence.JUSTIFIEE));
        poserLAbsence(AUTRE_CENTRE, absence(4, ETRANGER, JOUR.minusDays(5), StatutAbsence.A_QUALIFIER));

        assertThat(lecture().nombreAQualifier(CENTRE)).isEqualTo(2);
        assertThat(lecture().nombreAQualifierAvant(CENTRE, JOUR)).as("limite exclue").isEqualTo(1);
        assertThat(lecture().nombreAQualifierAvant(CENTRE, JOUR.plusDays(1))).isEqualTo(2);
        assertThat(lecture().nombreAQualifierAvant(CENTRE, JOUR.minusDays(1))).isZero();
        assertThat(lecture().nombreAQualifier(new CentreId(new UUID(0x7000, 0xc)))).isZero();
    }

    @Test
    void une_absence_se_lit_dans_son_centre_seulement() {
        lesPatients();
        poserLAbsence(CENTRE, rattrapee(1, AMRANI, JOUR, JOUR.plusDays(2)));
        poserLAbsence(AUTRE_CENTRE, absence(2, ETRANGER, JOUR, StatutAbsence.A_QUALIFIER));

        var lue = lecture().absence(CENTRE, id(1));

        assertThat(lue).isPresent();
        assertThat(lue.get().patient()).isEqualTo(AMRANI);
        assertThat(lue.get().dateDeSeance()).isEqualTo(JOUR);
        assertThat(lue.get().statut()).isEqualTo(StatutAbsence.RATTRAPEE);
        assertThat(lue.get().dateDeRattrapage()).isEqualTo(JOUR.plusDays(2));
        assertThat(lecture().absence(CENTRE, id(2))).as("absence d'un autre centre").isEmpty();
        assertThat(lecture().absence(CENTRE, id(99))).as("absence inconnue").isEmpty();
    }

    @Test
    void seules_les_absences_rattrapees_reservent_leur_date_de_rattrapage() {
        lesPatients();
        poserLAbsence(CENTRE, rattrapee(1, AMRANI, JOUR, JOUR.plusDays(2)));
        poserLAbsence(CENTRE, rattrapee(2, AMRANI, JOUR.minusDays(1), JOUR.plusDays(4)));
        AbsenceSuivie annulee = absence(3, AMRANI, JOUR.minusDays(2), StatutAbsence.ANNULEE);
        poserLAbsence(CENTRE, new AbsenceSuivie(annulee.id(), annulee.patient(), annulee.dateDeSeance(), annulee.source(),
                annulee.statut(), null, "Erreur", null, annulee.montantTtc(), annulee.tauxDeTva(), annulee.montantHt(),
                null, null, JOUR.plusDays(5)));
        poserLAbsence(CENTRE, rattrapee(4, BELKACEM, JOUR, JOUR.plusDays(3)));
        poserLAbsence(CENTRE, absence(5, AMRANI, JOUR.minusDays(3), StatutAbsence.A_QUALIFIER));

        assertThat(lecture().datesDeRattrapageUtilisees(CENTRE, AMRANI))
                .containsExactlyInAnyOrder(JOUR.plusDays(2), JOUR.plusDays(4));
        assertThat(lecture().datesDeRattrapageUtilisees(CENTRE, BELKACEM)).containsExactly(JOUR.plusDays(3));
        assertThat(lecture().datesDeRattrapageUtilisees(AUTRE_CENTRE, AMRANI)).isEmpty();
    }

    @Test
    void les_seances_realisees_du_centre_sont_les_validees_signees_ou_facturees_une_ligne_par_patient_et_par_jour() {
        poserLaSeance(CENTRE, AMRANI, JOUR, "VALIDEE");
        poserLaSeance(CENTRE, AMRANI, JOUR, "SIGNEE");
        poserLaSeance(CENTRE, BELKACEM, JOUR.plusDays(1), "FACTUREE");
        poserLaSeance(CENTRE, BELKACEM, JOUR.plusDays(2), "CREE");
        poserLaSeance(CENTRE, BELKACEM, JOUR.plusDays(3), "ABSENT");
        poserLaSeance(CENTRE, AMRANI, JOUR.minusDays(1), "VALIDEE");
        poserLaSeance(CENTRE, AMRANI, JOUR.plusDays(5), "VALIDEE");
        poserLaSeance(AUTRE_CENTRE, ETRANGER, JOUR, "VALIDEE");

        var realisees = seances().duCentre(CENTRE, JOUR, JOUR.plusDays(4));

        assertThat(realisees).containsExactly(new SeanceRealisee(AMRANI, JOUR), new SeanceRealisee(BELKACEM, JOUR.plusDays(1)));
        assertThat(seances().duCentre(CENTRE, JOUR.minusDays(1), JOUR.minusDays(1)))
                .as("bornes incluses").containsExactly(new SeanceRealisee(AMRANI, JOUR.minusDays(1)));
    }

    @Test
    void les_jours_de_seance_d_un_patient_sont_distincts_croissants_et_bornes_inclusivement() {
        poserLaSeance(CENTRE, AMRANI, JOUR.plusDays(3), "SIGNEE");
        poserLaSeance(CENTRE, AMRANI, JOUR.plusDays(1), "VALIDEE");
        poserLaSeance(CENTRE, AMRANI, JOUR.plusDays(1), "FACTUREE");
        poserLaSeance(CENTRE, AMRANI, JOUR.plusDays(2), "CREE");
        poserLaSeance(CENTRE, AMRANI, JOUR, "VALIDEE");
        poserLaSeance(CENTRE, AMRANI, JOUR.plusDays(9), "VALIDEE");
        poserLaSeance(CENTRE, BELKACEM, JOUR.plusDays(1), "VALIDEE");
        poserLaSeance(AUTRE_CENTRE, AMRANI, JOUR.plusDays(1), "VALIDEE");

        assertThat(seances().joursDuPatient(CENTRE, AMRANI, JOUR, JOUR.plusDays(3)))
                .containsExactly(JOUR, JOUR.plusDays(1), JOUR.plusDays(3));
        assertThat(seances().joursDuPatient(CENTRE, AMRANI, JOUR.plusDays(1), JOUR.plusDays(1))).containsExactly(JOUR.plusDays(1));
        assertThat(seances().joursDuPatient(CENTRE, AMRANI, JOUR.plusDays(4), JOUR.plusDays(8))).isEmpty();
        assertThat(seances().joursDuPatient(CENTRE, AMRANI, JOUR.plusDays(5), JOUR)).as("bornes inversées").isEmpty();
    }

    @Test
    void une_seance_est_realisee_ce_jour_la_si_elle_est_validee_signee_ou_facturee_pour_ce_patient_et_ce_centre() {
        poserLaSeance(CENTRE, AMRANI, JOUR, "VALIDEE");
        poserLaSeance(CENTRE, AMRANI, JOUR.plusDays(1), "SIGNEE");
        poserLaSeance(CENTRE, AMRANI, JOUR.plusDays(2), "FACTUREE");
        poserLaSeance(CENTRE, AMRANI, JOUR.plusDays(3), "CREE");
        poserLaSeance(CENTRE, AMRANI, JOUR.plusDays(4), "ABSENT");
        poserLaSeance(AUTRE_CENTRE, BELKACEM, JOUR, "VALIDEE");

        assertThat(seances().realisee(CENTRE, AMRANI, JOUR)).isTrue();
        assertThat(seances().realisee(CENTRE, AMRANI, JOUR.plusDays(1))).isTrue();
        assertThat(seances().realisee(CENTRE, AMRANI, JOUR.plusDays(2))).isTrue();
        assertThat(seances().realisee(CENTRE, AMRANI, JOUR.plusDays(3))).isFalse();
        assertThat(seances().realisee(CENTRE, AMRANI, JOUR.plusDays(4))).isFalse();
        assertThat(seances().realisee(CENTRE, AMRANI, JOUR.plusDays(5))).isFalse();
        assertThat(seances().realisee(CENTRE, BELKACEM, JOUR)).isFalse();
        assertThat(seances().realisee(AUTRE_CENTRE, AMRANI, JOUR)).isFalse();
    }
}
