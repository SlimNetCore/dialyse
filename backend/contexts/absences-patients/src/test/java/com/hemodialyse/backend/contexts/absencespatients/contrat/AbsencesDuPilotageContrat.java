package com.hemodialyse.backend.contexts.absencespatients.contrat;

import com.hemodialyse.backend.contexts.absencespatients.publishedlanguage.AbsencesDuPilotage;
import com.hemodialyse.backend.contexts.absencespatients.publishedlanguage.AbsencesDuPilotage.AbsenceDuCentre;
import com.hemodialyse.backend.contexts.absencespatients.publishedlanguage.AbsencesDuPilotage.AbsencesDuMois;
import com.hemodialyse.backend.contexts.absencespatients.publishedlanguage.AbsencesDuPilotage.CumulDuCentre;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

/**
 * Contrat du langage publié {@link AbsencesDuPilotage} (slice A4) : le même jeu de tests passe contre le double en mémoire, les
 * adaptateurs JDBC sur H2 et sur PostgreSQL 16 (le double est donc fidèle). Les requêtes sont celles du legacy : bornes incluses,
 * annulées exclues, isolation par centre.
 */
public abstract class AbsencesDuPilotageContrat {

    protected static final UUID CENTRE = new UUID(0x7000, 0xa);
    protected static final UUID AUTRE_CENTRE = new UUID(0x7000, 0xb);
    protected static final UUID TROISIEME_CENTRE = new UUID(0x7000, 0xc);
    protected static final UUID AMRANI = new UUID(0x7001, 1);
    protected static final UUID BELKACEM = new UUID(0x7001, 2);
    private static final LocalDate MARS_1 = LocalDate.of(2026, 3, 1);
    private static final LocalDate MARS_31 = LocalDate.of(2026, 3, 31);

    protected abstract AbsencesDuPilotage pilotage();

    protected abstract void poser(UUID centre, UUID patient, LocalDate date, String statut, String motif, BigDecimal ht,
                                  BigDecimal ttc);

    private void poser(UUID centre, UUID patient, String date, String statut, String motif, String ht, String ttc) {
        poser(centre, patient, LocalDate.parse(date), statut, motif, new BigDecimal(ht), new BigDecimal(ttc));
    }

    private void uneSelectionDAbsences() {
        poser(CENTRE, AMRANI, "2026-03-01", "A_QUALIFIER", null, "10000.00", "11900.00");
        poser(CENTRE, AMRANI, "2026-03-31", "JUSTIFIEE", "MALADIE", "10000.00", "11900.00");
        poser(CENTRE, BELKACEM, "2026-03-15", "NON_JUSTIFIEE", "AUTRE", "5000.50", "5950.60");
        poser(CENTRE, BELKACEM, "2026-03-16", "ANNULEE", null, "10000.00", "11900.00");
        poser(CENTRE, BELKACEM, "2026-03-20", "RATTRAPEE", "MALADIE", "10000.00", "11900.00");
        poser(CENTRE, AMRANI, "2026-02-28", "JUSTIFIEE", "MALADIE", "10000.00", "11900.00");
        poser(CENTRE, AMRANI, "2026-04-01", "JUSTIFIEE", "MALADIE", "10000.00", "11900.00");
        poser(AUTRE_CENTRE, BELKACEM, "2026-03-10", "A_QUALIFIER", null, "7000.00", "8330.00");
    }

    @Test
    void le_nombre_compte_les_bornes_et_ignore_les_annulees_et_les_autres_centres() {
        uneSelectionDAbsences();

        assertThat(pilotage().nombreHorsAnnulees(CENTRE, MARS_1, MARS_31)).isEqualTo(4);
        assertThat(pilotage().nombreHorsAnnulees(AUTRE_CENTRE, MARS_1, MARS_31)).isEqualTo(1);
        assertThat(pilotage().nombreHorsAnnulees(TROISIEME_CENTRE, MARS_1, MARS_31)).isZero();
        assertThat(pilotage().nombreHorsAnnulees(CENTRE, LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 30))).isEqualTo(2);
    }

    @Test
    void le_detail_rend_patient_jour_et_statut_des_absences_hors_annulees() {
        uneSelectionDAbsences();

        assertThat(pilotage().horsAnnulees(CENTRE, MARS_1, MARS_31)).containsExactlyInAnyOrder(
                new AbsenceDuCentre(AMRANI, LocalDate.of(2026, 3, 1), "A_QUALIFIER"),
                new AbsenceDuCentre(AMRANI, LocalDate.of(2026, 3, 31), "JUSTIFIEE"),
                new AbsenceDuCentre(BELKACEM, LocalDate.of(2026, 3, 15), "NON_JUSTIFIEE"),
                new AbsenceDuCentre(BELKACEM, LocalDate.of(2026, 3, 20), "RATTRAPEE"));
        assertThat(pilotage().horsAnnulees(TROISIEME_CENTRE, MARS_1, MARS_31)).isEmpty();
    }

    @Test
    void le_cumul_regroupe_par_centre_statut_et_motif_et_somme_les_montants() {
        uneSelectionDAbsences();
        poser(CENTRE, BELKACEM, "2026-03-05", "JUSTIFIEE", "MALADIE", "2500.25", "2975.30");

        List<CumulDuCentre> cumul = pilotage().cumulParCentre(List.of(CENTRE, AUTRE_CENTRE, TROISIEME_CENTRE), MARS_1, MARS_31);

        assertThat(cumul).hasSize(5);
        assertThat(cumul).anySatisfy(g -> {
            assertThat(g.centre()).isEqualTo(CENTRE);
            assertThat(g.statut()).isEqualTo("JUSTIFIEE");
            assertThat(g.motif()).isEqualTo("MALADIE");
            assertThat(g.nombre()).isEqualTo(2);
            assertThat(g.montantHt()).isEqualByComparingTo("12500.25");
            assertThat(g.montantTtc()).isEqualByComparingTo("14875.30");
        });
        assertThat(cumul).anySatisfy(g -> {
            assertThat(g.centre()).isEqualTo(CENTRE);
            assertThat(g.statut()).isEqualTo("A_QUALIFIER");
            assertThat(g.motif()).isNull();
            assertThat(g.nombre()).isEqualTo(1);
        });
        assertThat(cumul).noneMatch(g -> "ANNULEE".equals(g.statut()));
        assertThat(cumul).filteredOn(g -> g.centre().equals(AUTRE_CENTRE)).singleElement()
                .satisfies(g -> assertThat(g.montantHt()).isEqualByComparingTo("7000.00"));
    }

    @Test
    void le_cumul_d_un_ensemble_de_centres_vide_ou_d_une_periode_sans_absence_est_vide() {
        uneSelectionDAbsences();

        assertThat(pilotage().cumulParCentre(List.of(), MARS_1, MARS_31)).isEmpty();
        assertThat(pilotage().cumulParCentre(List.of(CENTRE), LocalDate.of(2027, 1, 1), LocalDate.of(2027, 12, 31))).isEmpty();
    }

    @Test
    void le_mensuel_ne_garde_que_les_statuts_comptabilises_par_mois_croissant() {
        uneSelectionDAbsences();

        List<AbsencesDuMois> mois = pilotage().mensuel(List.of(CENTRE, AUTRE_CENTRE), LocalDate.of(2026, 2, 1),
                LocalDate.of(2026, 4, 30));

        assertThat(mois).extracting(AbsencesDuMois::annee, AbsencesDuMois::mois, AbsencesDuMois::nombre)
                .containsExactly(tuple(2026, 2, 1L), tuple(2026, 3, 4L), tuple(2026, 4, 1L));
        assertThat(mois.get(1).montantHt()).isEqualByComparingTo("32000.50");
    }

    @Test
    void le_mensuel_est_limite_aux_centres_demandes_et_vide_sans_centre() {
        uneSelectionDAbsences();

        assertThat(pilotage().mensuel(List.of(AUTRE_CENTRE), MARS_1, MARS_31)).singleElement()
                .satisfies(m -> assertThat(m.nombre()).isEqualTo(1));
        assertThat(pilotage().mensuel(List.of(), MARS_1, MARS_31)).isEmpty();
    }
}
