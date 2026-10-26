package com.hemodialyse.backend.contexts.planification.contrat;

import com.hemodialyse.backend.contexts.planification.domain.AbsenceInfirmier;
import com.hemodialyse.backend.contexts.planification.domain.AbsencesRepository;
import com.hemodialyse.backend.contexts.planification.domain.EcritureConcurrente;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.AbsenceId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.AffectationId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CompteId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CreneauId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.InfirmierId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.RemplacementId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.SalleId;
import com.hemodialyse.backend.contexts.planification.domain.JourDeLaSemaine;
import com.hemodialyse.backend.contexts.planification.domain.Pagination;
import com.hemodialyse.backend.contexts.planification.domain.PeriodeDAbsence;
import com.hemodialyse.backend.contexts.planification.domain.RemplacantEligible;
import com.hemodialyse.backend.contexts.planification.domain.Remplacement;
import com.hemodialyse.backend.contexts.planification.domain.RemplacementsRepository;
import com.hemodialyse.backend.contexts.planification.domain.RoulementDeLInfirmier;
import com.hemodialyse.backend.contexts.planification.domain.RoulementsRepository;
import com.hemodialyse.backend.contexts.planification.domain.TypeAbsence;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Contrat du roulement, des absences et des remplacements : exécuté contre les doubles en mémoire, les adaptateurs JDBC sur H2 et les
 * mêmes adaptateurs sur PostgreSQL 16 ({@code PlanningDesInfirmiersJdbcContrat}). Le double passe le même jeu de tests que l'adaptateur réel.
 */
public abstract class PlanningDesInfirmiersContrat {

    protected static final CentreId CENTRE = new CentreId(UUID.fromString("99997814-0000-0000-0000-00000000000a"));
    protected static final CentreId AUTRE = new CentreId(UUID.fromString("99997814-0000-0000-0000-00000000000b"));
    protected static final LocalDate JOUR = LocalDate.of(2026, 11, 2);

    protected abstract RoulementsRepository roulements();

    protected abstract AbsencesRepository absences();

    protected abstract RemplacementsRepository remplacements();

    private static InfirmierId nouvelInfirmier() {
        return new InfirmierId(UUID.randomUUID());
    }

    private static AbsenceInfirmier absence(CentreId centre, InfirmierId infirmier, LocalDate debut, LocalDate fin, CompteId auteur) {
        return AbsenceInfirmier.declarer(new AbsenceId(UUID.randomUUID()), centre, infirmier, new PeriodeDAbsence(debut, fin),
                TypeAbsence.MALADIE, "Grippe", auteur);
    }

    // ------------------------------------------------------------------ roulement

    protected void poser(InfirmierId infirmier, AffectationId id, SalleId salle, CreneauId creneau, java.util.Set<JourDeLaSemaine> jours) {
        roulements().ajouter(CENTRE, infirmier, new com.hemodialyse.backend.contexts.planification.domain.Affectation(id, salle, creneau, jours));
    }

    @Test
    void should_read_back_the_rotation_of_a_nurse_and_hide_it_from_another_centre() {
        InfirmierId sara = nouvelInfirmier();
        SalleId salle = new SalleId(UUID.randomUUID());
        CreneauId matin = new CreneauId(UUID.randomUUID());
        AffectationId a = new AffectationId(UUID.randomUUID());

        poser(sara, a, salle, matin, EnumSet.of(JourDeLaSemaine.MERCREDI, JourDeLaSemaine.LUNDI));

        RoulementDeLInfirmier lu = roulements().de(CENTRE, sara);
        assertThat(lu.affectation(a).orElseThrow().salle()).isEqualTo(salle);
        assertThat(lu.affectation(a).orElseThrow().jours()).containsExactly(JourDeLaSemaine.LUNDI, JourDeLaSemaine.MERCREDI);
        assertThat(roulements().de(AUTRE, sara).affectations()).isEmpty();
        assertThat(roulements().de(CENTRE, nouvelInfirmier()).affectations()).isEmpty();
    }

    @Test
    void should_write_only_the_touched_assignment_without_touching_another_nurse_or_another_row_of_the_same_nurse() {
        InfirmierId sara = nouvelInfirmier();
        InfirmierId lina = nouvelInfirmier();
        SalleId salle = new SalleId(UUID.randomUUID());
        CreneauId matin = new CreneauId(UUID.randomUUID());
        CreneauId soir = new CreneauId(UUID.randomUUID());
        AffectationId a = new AffectationId(UUID.randomUUID());
        AffectationId b = new AffectationId(UUID.randomUUID());
        AffectationId c = new AffectationId(UUID.randomUUID());
        poser(lina, new AffectationId(UUID.randomUUID()), salle, matin, EnumSet.of(JourDeLaSemaine.LUNDI));
        poser(sara, a, salle, matin, EnumSet.of(JourDeLaSemaine.LUNDI));
        poser(sara, b, salle, soir, EnumSet.of(JourDeLaSemaine.MARDI));
        // une affectation écrite par ailleurs (l'optimisation) après la lecture du roulement
        RoulementDeLInfirmier lu = roulements().de(CENTRE, sara);
        poser(sara, c, salle, new CreneauId(UUID.randomUUID()), EnumSet.of(JourDeLaSemaine.VENDREDI));

        roulements().remplacer(CENTRE, sara, lu.modifier(a, salle, matin, EnumSet.of(JourDeLaSemaine.JEUDI)).affectation(a).orElseThrow());
        roulements().retirer(CENTRE, sara, b);

        RoulementDeLInfirmier apres = roulements().de(CENTRE, sara);
        assertThat(apres.affectations()).extracting(x -> x.id()).containsExactlyInAnyOrder(a, c);
        assertThat(apres.affectation(a).orElseThrow().jours()).containsExactly(JourDeLaSemaine.JEUDI);
        assertThat(roulements().de(CENTRE, lina).affectations()).hasSize(1);
        roulements().retirer(AUTRE, sara, a);
        assertThat(roulements().de(CENTRE, sara).affectations()).hasSize(2);
    }

    // ------------------------------------------------------------------ absences

    @Test
    void should_read_back_an_absence_with_its_author_and_keep_a_missing_author_missing() {
        InfirmierId sara = nouvelInfirmier();
        CompteId auteur = new CompteId(UUID.randomUUID());
        AbsenceInfirmier avec = absence(CENTRE, sara, JOUR, JOUR.plusDays(2), auteur);
        AbsenceInfirmier sans = absence(CENTRE, sara, JOUR.plusDays(10), JOUR.plusDays(11), null);

        absences().enregistrer(avec);
        absences().enregistrer(sans);

        AbsenceInfirmier lue = absences().parId(CENTRE, avec.id()).orElseThrow();
        assertThat(lue.auteur()).contains(auteur);
        assertThat(lue.motif()).contains("Grippe");
        assertThat(lue.periode()).isEqualTo(avec.periode());
        assertThat(absences().parId(CENTRE, sans.id()).orElseThrow().auteur()).isEmpty();
        assertThat(absences().parId(AUTRE, avec.id())).isEmpty();
    }

    @Test
    void should_list_absences_from_the_most_recent_start_date_with_the_effective_page_size() {
        InfirmierId sara = nouvelInfirmier();
        InfirmierId lina = nouvelInfirmier();
        AbsenceInfirmier ancienne = absence(CENTRE, sara, JOUR, JOUR, null);
        AbsenceInfirmier recente = absence(CENTRE, sara, JOUR.plusDays(20), JOUR.plusDays(21), null);
        AbsenceInfirmier deLina = absence(CENTRE, lina, JOUR.plusDays(10), JOUR.plusDays(10), null);
        absences().enregistrer(ancienne);
        absences().enregistrer(recente);
        absences().enregistrer(deLina);
        absences().enregistrer(absence(AUTRE, sara, JOUR, JOUR, null));

        var page = absences().page(CENTRE, new Pagination(0, 2));
        var deSara = absences().pageDeLInfirmier(CENTRE, sara, new Pagination(0, 500));

        assertThat(page.elements()).extracting(AbsenceInfirmier::id).containsExactly(recente.id(), deLina.id());
        assertThat(page.total()).isEqualTo(3);
        assertThat(page.taille()).isEqualTo(2);
        assertThat(deSara.elements()).extracting(AbsenceInfirmier::id).containsExactly(recente.id(), ancienne.id());
        assertThat(deSara.taille()).isEqualTo(100);
        assertThat(absences().page(CENTRE, new Pagination(0, 0)).elements()).isEmpty();
        assertThat(absences().page(CENTRE, new Pagination(0, 0)).total()).isEqualTo(3);
    }

    @Test
    void should_find_an_absence_sharing_a_day_with_a_period_and_none_for_contiguous_periods_or_other_nurses() {
        InfirmierId sara = nouvelInfirmier();
        absences().enregistrer(absence(CENTRE, sara, JOUR, JOUR.plusDays(4), null));

        assertThat(absences().existeUneAbsenceSurLaPeriode(CENTRE, sara, new PeriodeDAbsence(JOUR.plusDays(4), JOUR.plusDays(9)))).isTrue();
        assertThat(absences().existeUneAbsenceSurLaPeriode(CENTRE, sara, new PeriodeDAbsence(JOUR.plusDays(1), JOUR.plusDays(2)))).isTrue();
        assertThat(absences().existeUneAbsenceSurLaPeriode(CENTRE, sara, new PeriodeDAbsence(JOUR.minusDays(5), JOUR))).isTrue();
        assertThat(absences().existeUneAbsenceSurLaPeriode(CENTRE, sara, new PeriodeDAbsence(JOUR.plusDays(5), JOUR.plusDays(9)))).isFalse();
        assertThat(absences().existeUneAbsenceSurLaPeriode(CENTRE, sara, new PeriodeDAbsence(JOUR.minusDays(5), JOUR.minusDays(1)))).isFalse();
        assertThat(absences().existeUneAbsenceSurLaPeriode(CENTRE, nouvelInfirmier(), new PeriodeDAbsence(JOUR, JOUR))).isFalse();
        assertThat(absences().existeUneAbsenceSurLaPeriode(AUTRE, sara, new PeriodeDAbsence(JOUR, JOUR))).isFalse();
    }

    @Test
    void should_tell_whether_a_nurse_is_absent_on_a_day_bounds_included_and_remove_an_absence() {
        InfirmierId sara = nouvelInfirmier();
        AbsenceInfirmier a = absence(CENTRE, sara, JOUR, JOUR.plusDays(2), null);
        absences().enregistrer(a);

        assertThat(absences().absentLe(CENTRE, sara, JOUR)).isTrue();
        assertThat(absences().absentLe(CENTRE, sara, JOUR.plusDays(2))).isTrue();
        assertThat(absences().absentLe(CENTRE, sara, JOUR.plusDays(3))).isFalse();
        assertThat(absences().absentLe(CENTRE, sara, JOUR.minusDays(1))).isFalse();
        absences().retirer(AUTRE, a.id());
        assertThat(absences().parId(CENTRE, a.id())).isPresent();
        absences().retirer(CENTRE, a.id());
        assertThat(absences().parId(CENTRE, a.id())).isEmpty();
        assertThat(absences().absentLe(CENTRE, sara, JOUR)).isFalse();
    }

    // ------------------------------------------------------------------ remplacements

    private Remplacement remplacement(CentreId centre, InfirmierId remplacant, LocalDate jour, SalleId salle, CreneauId creneau,
                                      InfirmierId remplace) {
        return Remplacement.affecter(new RemplacementId(UUID.randomUUID()), centre, jour, salle, creneau,
                RemplacantEligible.prouver(new RemplacantEligible.Situation(remplacant, true, true, false, false, false)), remplace);
    }

    @Test
    void should_read_back_a_replacement_and_cancel_it_within_its_centre_only() {
        InfirmierId renfort = nouvelInfirmier();
        InfirmierId titulaire = nouvelInfirmier();
        SalleId salle = new SalleId(UUID.randomUUID());
        CreneauId matin = new CreneauId(UUID.randomUUID());
        Remplacement r = remplacement(CENTRE, renfort, JOUR, salle, matin, titulaire);

        remplacements().enregistrer(r);

        Remplacement lu = remplacements().parId(CENTRE, r.id()).orElseThrow();
        assertThat(lu.remplacant()).isEqualTo(renfort);
        assertThat(lu.remplace()).contains(titulaire);
        assertThat(lu.date()).isEqualTo(JOUR);
        assertThat(remplacements().parId(AUTRE, r.id())).isEmpty();
        remplacements().annuler(AUTRE, r.id());
        assertThat(remplacements().parId(CENTRE, r.id())).isPresent();
        remplacements().annuler(CENTRE, r.id());
        assertThat(remplacements().parId(CENTRE, r.id())).isEmpty();
    }

    @Test
    void should_know_a_nurse_replaces_already_on_a_day_and_slot_in_any_room() {
        InfirmierId renfort = nouvelInfirmier();
        CreneauId matin = new CreneauId(UUID.randomUUID());
        remplacements().enregistrer(remplacement(CENTRE, renfort, JOUR, new SalleId(UUID.randomUUID()), matin, null));

        assertThat(remplacements().parId(CENTRE, new RemplacementId(UUID.randomUUID()))).isEmpty();
        assertThat(remplacements().remplaceDeja(CENTRE, renfort, JOUR, matin)).isTrue();
        assertThat(remplacements().remplaceDeja(CENTRE, renfort, JOUR.plusDays(1), matin)).isFalse();
        assertThat(remplacements().remplaceDeja(CENTRE, renfort, JOUR, new CreneauId(UUID.randomUUID()))).isFalse();
        assertThat(remplacements().remplaceDeja(CENTRE, nouvelInfirmier(), JOUR, matin)).isFalse();
        assertThat(remplacements().remplaceDeja(AUTRE, renfort, JOUR, matin)).isFalse();
    }

    @Test
    void should_refuse_the_same_nurse_on_the_same_day_and_slot_twice_as_a_concurrent_write() {
        InfirmierId renfort = nouvelInfirmier();
        CreneauId matin = new CreneauId(UUID.randomUUID());
        remplacements().enregistrer(remplacement(CENTRE, renfort, JOUR, new SalleId(UUID.randomUUID()), matin, null));

        assertThatThrownBy(() -> remplacements().enregistrer(remplacement(CENTRE, renfort, JOUR, new SalleId(UUID.randomUUID()), matin, null)))
                .isInstanceOf(EcritureConcurrente.class);
    }
}
