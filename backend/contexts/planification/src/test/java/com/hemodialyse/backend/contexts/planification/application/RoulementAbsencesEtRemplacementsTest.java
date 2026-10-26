package com.hemodialyse.backend.contexts.planification.application;

import com.hemodialyse.backend.contexts.planification.doubles.PlanningDesInfirmiersEnMemoire;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.SemaineDeLInfirmier;
import com.hemodialyse.backend.contexts.planification.domain.EcritureConcurrente;
import com.hemodialyse.backend.contexts.planification.domain.IdentiteDeLInfirmier;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.AbsenceId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.AffectationId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CompteId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CreneauId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.InfirmierId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.SalleId;
import com.hemodialyse.backend.contexts.planification.domain.Infirmier;
import com.hemodialyse.backend.contexts.planification.domain.JourDeLaSemaine;
import com.hemodialyse.backend.contexts.planification.domain.Matricule;
import com.hemodialyse.backend.contexts.planification.domain.Pagination;
import com.hemodialyse.backend.contexts.planification.domain.PeriodeDAbsence;
import com.hemodialyse.backend.contexts.planification.domain.Qualification;
import com.hemodialyse.backend.contexts.planification.domain.RegleDePlanificationViolee;
import com.hemodialyse.backend.contexts.planification.domain.SaisieInvalide;
import com.hemodialyse.backend.contexts.planification.domain.TypeAbsence;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cas d'usage du roulement, des absences, des remplacements et de « Mon planning » avec les doubles en mémoire (P14 à P16, matrice
 * invariant → test) : verrou de la fiche avant toute écriture, ordre des contrôles, notifications après l'enregistrement.
 */
class RoulementAbsencesEtRemplacementsTest {

    private static final CentreId CENTRE = PlanningDesInfirmiersEnMemoire.CENTRE;
    private static final LocalDate LUNDI = LocalDate.of(2026, 11, 2);

    private final PlanningDesInfirmiersEnMemoire m = new PlanningDesInfirmiersEnMemoire();
    private final Map<String, UUID> grille = m.grille(CENTRE, false);
    private final SalleId salle = new SalleId(grille.get("salle"));
    private final CreneauId creneau = new CreneauId(grille.get("creneau"));

    private final AffecterAuRoulement affecter = new AffecterAuRoulement(m.unite, m.monde.infirmiers, m.roulements,
            m.parametrage.salles, m.creneaux, m.identifiants, m.surEffectifDuRoulement, m.horloge);
    private final ModifierLAffectation modifier = new ModifierLAffectation(m.unite, m.monde.infirmiers, m.roulements,
            m.parametrage.salles, m.creneaux, m.surEffectifDuRoulement, m.horloge);
    private final RetirerLAffectation retirer = new RetirerLAffectation(m.unite, m.monde.infirmiers, m.roulements);
    private final DeclarerUneAbsenceDInfirmier declarer = new DeclarerUneAbsenceDInfirmier(m.unite, m.monde.infirmiers, m.absences,
            m.identifiants, m.notificationsDuCentre);
    private final RetirerUneAbsence retirerAbsence = new RetirerUneAbsence(m.unite, m.absences, m.monde.infirmiers, m.notificationsDuCentre);
    private final ListerLesAbsences lister = new ListerLesAbsences(m.absences);
    private final DeclarerMonAbsence declarerMonAbsence = new DeclarerMonAbsence(m.unite, m.monde.infirmiers, m.absences, m.identifiants,
            m.notificationsDuCentre, m.horloge);
    private final RetirerMonAbsence retirerMonAbsence = new RetirerMonAbsence(m.unite, m.monde.infirmiers, m.absences,
            m.notificationsDuCentre, m.horloge);
    private final ListerMesAbsences listerMesAbsences = new ListerMesAbsences(m.monde.infirmiers, m.absences);
    private final AffecterUnRemplacant remplacer = new AffecterUnRemplacant(m.unite, m.monde.infirmiers, m.roulements, m.absences,
            m.remplacements, m.parametrage.salles, m.creneaux, m.calendrier, m.horloge, m.identifiants);
    private final AnnulerLeRemplacement annuler = new AnnulerLeRemplacement(m.unite, m.remplacements);

    private Infirmier fiche(String matricule, boolean habilite) {
        Infirmier f = Infirmier.enregistrer(new InfirmierId(UUID.randomUUID()), CENTRE, Matricule.depuisLaSaisie(matricule),
                new IdentiteDeLInfirmier("Nom" + matricule, "Sara", null), Qualification.INFIRMIER, habilite);
        m.monde.infirmiers.enregistrer(f);
        return f;
    }

    private static void refuse(Runnable action, String code) {
        assertThatThrownBy(action::run).isInstanceOf(RegleDePlanificationViolee.class)
                .extracting(e -> ((RegleDePlanificationViolee) e).code()).isEqualTo(code);
    }

    private static PeriodeDAbsence periode(LocalDate debut, LocalDate fin) {
        return new PeriodeDAbsence(debut, fin);
    }

    // ------------------------------------------------------------------ roulement

    @Test
    void should_lock_the_nurse_record_before_assigning_her_and_return_the_oversupply_warning() {
        Infirmier sara = fiche("M1", false);
        m.sureffectif = List.of(JourDeLaSemaine.LUNDI);

        var resultat = affecter.executer(CENTRE, sara.id(), salle, creneau, EnumSet.of(JourDeLaSemaine.LUNDI, JourDeLaSemaine.MARDI));

        assertThat(m.monde.fichesVerrouillees).containsExactly(sara.id());
        assertThat(resultat.affectation().jours()).containsExactly(JourDeLaSemaine.LUNDI, JourDeLaSemaine.MARDI);
        assertThat(resultat.joursEnSureffectif()).containsExactly(JourDeLaSemaine.LUNDI);
        assertThat(m.transactions).isEqualTo(1);
    }

    @Test
    void should_not_fail_the_assignment_when_the_oversupply_computation_fails() {
        Infirmier sara = fiche("M1", false);
        m.sureffectifEnEchec = true;

        var resultat = affecter.executer(CENTRE, sara.id(), salle, creneau, EnumSet.of(JourDeLaSemaine.LUNDI));

        assertThat(resultat.joursEnSureffectif()).isEmpty();
        assertThat(m.roulements.de(CENTRE, sara.id()).affectations()).hasSize(1);
    }

    @Test
    void should_check_the_nurse_then_the_room_then_the_slot_then_the_overlap_in_that_order() {
        Infirmier sara = fiche("M1", false);
        InfirmierId inconnu = new InfirmierId(UUID.randomUUID());
        SalleId autreSalle = new SalleId(UUID.randomUUID());
        CreneauId autreCreneau = new CreneauId(UUID.randomUUID());

        refuse(() -> affecter.executer(CENTRE, inconnu, autreSalle, autreCreneau, EnumSet.of(JourDeLaSemaine.LUNDI)), "INFIRMIER_INTROUVABLE");
        refuse(() -> affecter.executer(CENTRE, sara.id(), autreSalle, autreCreneau, EnumSet.of(JourDeLaSemaine.LUNDI)),
                "AFFECTATION_SALLE_INCONNUE");
        refuse(() -> affecter.executer(CENTRE, sara.id(), salle, autreCreneau, EnumSet.of(JourDeLaSemaine.LUNDI)),
                "AFFECTATION_CRENEAU_INCONNU");
        affecter.executer(CENTRE, sara.id(), salle, creneau, EnumSet.of(JourDeLaSemaine.LUNDI));
        refuse(() -> affecter.executer(CENTRE, sara.id(), salle, creneau, EnumSet.of(JourDeLaSemaine.LUNDI)), "AFFECTATION_CHEVAUCHEMENT");
    }

    @Test
    void should_refuse_to_assign_a_deactivated_nurse_but_still_let_her_assignments_be_removed() {
        Infirmier sara = fiche("M1", false);
        var resultat = affecter.executer(CENTRE, sara.id(), salle, creneau, EnumSet.of(JourDeLaSemaine.LUNDI));
        m.monde.infirmiers.enregistrer(sara.desactiver());

        refuse(() -> affecter.executer(CENTRE, sara.id(), salle, creneau, EnumSet.of(JourDeLaSemaine.MARDI)), "INFIRMIER_INACTIF");
        refuse(() -> modifier.executer(CENTRE, sara.id(), new AffectationId(resultat.affectation().id()), salle, creneau,
                EnumSet.of(JourDeLaSemaine.MARDI)), "INFIRMIER_INACTIF");
        retirer.executer(CENTRE, sara.id(), new AffectationId(resultat.affectation().id()));
        assertThat(m.roulements.de(CENTRE, sara.id()).affectations()).isEmpty();
    }

    @Test
    void should_modify_and_remove_an_assignment_and_refuse_that_of_another_nurse() {
        Infirmier sara = fiche("M1", false);
        Infirmier lina = fiche("M2", false);
        var a = affecter.executer(CENTRE, sara.id(), salle, creneau, EnumSet.of(JourDeLaSemaine.LUNDI)).affectation();

        var modifiee = modifier.executer(CENTRE, sara.id(), new AffectationId(a.id()), salle, creneau,
                EnumSet.of(JourDeLaSemaine.LUNDI, JourDeLaSemaine.JEUDI));
        refuse(() -> retirer.executer(CENTRE, lina.id(), new AffectationId(a.id())), "AFFECTATION_INTROUVABLE");
        retirer.executer(CENTRE, sara.id(), new AffectationId(a.id()));

        assertThat(modifiee.affectation().jours()).containsExactly(JourDeLaSemaine.LUNDI, JourDeLaSemaine.JEUDI);
        assertThat(m.roulements.de(CENTRE, sara.id()).affectations()).isEmpty();
    }

    // ------------------------------------------------------------------ absences (administration)

    @Test
    void should_declare_an_absence_and_warn_the_doctor_and_also_the_administrator_when_the_secretary_enters_it() {
        Infirmier sara = fiche("M1", false);
        CompteId secretaire = new CompteId(UUID.randomUUID());

        var absence = declarer.executer(CENTRE, sara.id(), periode(LUNDI, LUNDI.plusDays(2)), TypeAbsence.CONGE, "  Congé  ", secretaire, false);

        assertThat(absence.motif()).isEqualTo("Congé");
        assertThat(m.notifications).hasSize(1);
        assertThat(m.notifications.get(0)).startsWith("ENREGISTREE").endsWith("admin=true");
    }

    @Test
    void should_refuse_an_absence_overlapping_another_one_of_the_same_nurse_but_accept_contiguous_ones_and_other_nurses() {
        Infirmier sara = fiche("M1", false);
        Infirmier lina = fiche("M2", false);
        CompteId admin = new CompteId(UUID.randomUUID());
        declarer.executer(CENTRE, sara.id(), periode(LUNDI, LUNDI.plusDays(4)), TypeAbsence.CONGE, null, admin, true);

        refuse(() -> declarer.executer(CENTRE, sara.id(), periode(LUNDI, LUNDI.plusDays(4)), TypeAbsence.MALADIE, null, admin, true),
                "ABSENCE_CHEVAUCHEMENT");
        refuse(() -> declarer.executer(CENTRE, sara.id(), periode(LUNDI.plusDays(4), LUNDI.plusDays(8)), TypeAbsence.MALADIE, null, admin,
                true), "ABSENCE_CHEVAUCHEMENT");
        declarer.executer(CENTRE, sara.id(), periode(LUNDI.plusDays(5), LUNDI.plusDays(8)), TypeAbsence.MALADIE, null, admin, true);
        declarer.executer(CENTRE, lina.id(), periode(LUNDI, LUNDI.plusDays(4)), TypeAbsence.CONGE, null, admin, true);
        assertThat(lister.executer(CENTRE, new Pagination(0, 20)).total()).isEqualTo(3);
    }

    @Test
    void should_refuse_an_absence_for_an_unknown_or_deactivated_nurse_and_an_unusable_reason_before_any_lock() {
        Infirmier sara = fiche("M1", false);
        CompteId admin = new CompteId(UUID.randomUUID());
        m.monde.infirmiers.enregistrer(sara.desactiver());

        refuse(() -> declarer.executer(CENTRE, new InfirmierId(UUID.randomUUID()), periode(LUNDI, LUNDI), TypeAbsence.CONGE, null, admin, true),
                "INFIRMIER_INTROUVABLE");
        refuse(() -> declarer.executer(CENTRE, sara.id(), periode(LUNDI, LUNDI), TypeAbsence.CONGE, null, admin, true), "INFIRMIER_INACTIF");
        m.monde.fichesVerrouillees.clear();
        assertThatThrownBy(() -> declarer.executer(CENTRE, sara.id(), periode(LUNDI, LUNDI), TypeAbsence.CONGE, "x".repeat(256), admin, true))
                .isInstanceOf(SaisieInvalide.class);
        assertThat(m.monde.fichesVerrouillees).isEmpty();
    }

    @Test
    void should_notify_the_withdrawal_of_an_absence_whoever_entered_it() {
        Infirmier sara = fiche("M1", false);
        CompteId admin = new CompteId(UUID.randomUUID());
        var absence = declarer.executer(CENTRE, sara.id(), periode(LUNDI.minusDays(10), LUNDI.minusDays(5)), TypeAbsence.CONGE, null, admin, true);
        m.notifications.clear();

        retirerAbsence.executer(CENTRE, new AbsenceId(absence.id()), true);

        assertThat(m.notifications).hasSize(1);
        assertThat(m.notifications.get(0)).startsWith("RETIREE_ADMIN").endsWith("admin=false");
        refuse(() -> retirerAbsence.executer(CENTRE, new AbsenceId(absence.id()), true), "ABSENCE_INTROUVABLE");
    }

    // ------------------------------------------------------------------ Mon planning

    private CompteId compteLie(Infirmier f) {
        CompteId compte = new CompteId(UUID.randomUUID());
        m.monde.infirmiers.enregistrer(f.lierCompte(compte, true));
        return compte;
    }

    @Test
    void should_show_my_planning_of_the_current_week_of_the_centre_by_default_even_for_a_deactivated_nurse() {
        Infirmier sara = fiche("M1", false);
        CompteId compte = compteLie(sara);
        m.aujourdhui = LUNDI;
        m.semaine = new SemaineDeLInfirmier.PlanningDeLaSemaine(LUNDI, LUNDI.plusDays(6), List.of(), List.of(), List.of(), List.of(),
                List.of());
        ConsulterMonPlanning consulter = new ConsulterMonPlanning(m.monde.infirmiers,
                new FichesDetaillees(m.monde.roulementsDesInfirmiers, m.monde.comptesUtilisateurs), m.semaineDeLInfirmier, m.horloge);

        var parDefaut = consulter.executer(CENTRE, compte, null);
        assertThat(m.dateDemandee).isEqualTo(LUNDI);
        assertThat(parDefaut.infirmier().id()).isEqualTo(sara.id().valeur());
        assertThat(parDefaut.semaine().debut()).isEqualTo(LUNDI);
        consulter.executer(CENTRE, compte, LUNDI.plusDays(9));
        assertThat(m.dateDemandee).isEqualTo(LUNDI.plusDays(9));

        m.monde.infirmiers.enregistrer(m.monde.infirmiers.parId(CENTRE, sara.id()).orElseThrow().desactiver());
        assertThat(consulter.executer(CENTRE, compte, null).infirmier().actif()).isFalse();
        refuse(() -> consulter.executer(CENTRE, new CompteId(UUID.randomUUID()), null), "INFIRMIER_NON_LIE");
    }

    @Test
    void should_judge_today_at_the_centre_day_when_a_nurse_declares_an_absence() {
        Infirmier sara = fiche("M1", false);
        CompteId compte = compteLie(sara);
        m.aujourdhui = LUNDI;

        declarerMonAbsence.executer(CENTRE, compte, periode(LUNDI.minusDays(2), LUNDI), TypeAbsence.MALADIE, null);
        refuse(() -> declarerMonAbsence.executer(CENTRE, compte, periode(LUNDI.minusDays(3), LUNDI.minusDays(1)), TypeAbsence.MALADIE, null),
                "ABSENCE_PASSEE");
        assertThat(m.notifications.get(0)).startsWith("DECLAREE");
    }

    @Test
    void should_refuse_a_nurse_without_linked_record_in_the_centre() {
        CompteId inconnu = new CompteId(UUID.randomUUID());

        refuse(() -> declarerMonAbsence.executer(CENTRE, inconnu, periode(LUNDI, LUNDI), TypeAbsence.AUTRE, null), "INFIRMIER_NON_LIE");
        refuse(() -> listerMesAbsences.executer(CENTRE, inconnu, new Pagination(0, 20)), "INFIRMIER_NON_LIE");
        refuse(() -> retirerMonAbsence.executer(CENTRE, inconnu, new AbsenceId(UUID.randomUUID())), "INFIRMIER_NON_LIE");
    }

    @Test
    void should_refuse_an_absence_overlapping_one_of_hers_even_through_my_planning() {
        Infirmier sara = fiche("M1", false);
        CompteId compte = compteLie(sara);
        m.aujourdhui = LUNDI;
        declarerMonAbsence.executer(CENTRE, compte, periode(LUNDI.plusDays(3), LUNDI.plusDays(6)), TypeAbsence.CONGE, null);

        refuse(() -> declarerMonAbsence.executer(CENTRE, compte, periode(LUNDI.plusDays(5), LUNDI.plusDays(9)), TypeAbsence.CONGE, null),
                "ABSENCE_CHEVAUCHEMENT");
    }

    @Test
    void should_let_a_nurse_withdraw_only_her_own_future_absences_not_those_entered_by_the_administration() {
        Infirmier sara = fiche("M1", false);
        Infirmier lina = fiche("M2", false);
        CompteId compte = compteLie(sara);
        m.aujourdhui = LUNDI;
        CompteId admin = new CompteId(UUID.randomUUID());
        var maDeclaration = declarerMonAbsence.executer(CENTRE, compte, periode(LUNDI.plusDays(3), LUNDI.plusDays(4)), TypeAbsence.CONGE, null);
        var parAdministration = declarer.executer(CENTRE, sara.id(), periode(LUNDI.plusDays(10), LUNDI.plusDays(11)), TypeAbsence.CONGE,
                null, admin, true);
        var cellDeLina = declarer.executer(CENTRE, lina.id(), periode(LUNDI.plusDays(3), LUNDI.plusDays(4)), TypeAbsence.CONGE, null, admin, true);

        refuse(() -> retirerMonAbsence.executer(CENTRE, compte, new AbsenceId(parAdministration.id())), "ABSENCE_NON_ANNULABLE");
        refuse(() -> retirerMonAbsence.executer(CENTRE, compte, new AbsenceId(cellDeLina.id())), "ABSENCE_INTROUVABLE");
        m.notifications.clear();
        retirerMonAbsence.executer(CENTRE, compte, new AbsenceId(maDeclaration.id()));

        assertThat(m.notifications).hasSize(1);
        assertThat(m.notifications.get(0)).startsWith("RETIREE_INFIRMIER");
        assertThat(listerMesAbsences.executer(CENTRE, compte, new Pagination(0, 20)).elements()).extracting(AbsenceLue::id)
                .containsExactly(parAdministration.id());
    }

    // ------------------------------------------------------------------ remplacements

    @Test
    void should_assign_an_eligible_replacement_after_locking_the_replacement_nurse() {
        Infirmier titulaire = fiche("M1", false);
        Infirmier renfort = fiche("M2", false);
        affecter.executer(CENTRE, titulaire.id(), salle, creneau, EnumSet.of(JourDeLaSemaine.LUNDI));
        m.monde.fichesVerrouillees.clear();

        var r = remplacer.executer(CENTRE, LUNDI, salle, creneau, renfort.id(), titulaire.id());

        assertThat(m.monde.fichesVerrouillees).containsExactly(renfort.id());
        assertThat(r.infirmier()).isEqualTo(renfort.id().valeur());
        assertThat(r.remplace()).isEqualTo(titulaire.id().valeur());
    }

    @Test
    void should_check_that_the_nurse_does_not_replace_herself_before_anything_else() {
        Infirmier sara = fiche("M1", false);

        assertThatThrownBy(() -> remplacer.executer(CENTRE, LUNDI.minusDays(30), new SalleId(UUID.randomUUID()), creneau, sara.id(), sara.id()))
                .isInstanceOf(SaisieInvalide.class);
    }

    @Test
    void should_refuse_a_past_date_and_a_closed_day() {
        Infirmier renfort = fiche("M2", false);
        m.aujourdhui = LUNDI;
        m.joursFermes.add(LUNDI.plusDays(3));

        refuse(() -> remplacer.executer(CENTRE, LUNDI.minusDays(1), salle, creneau, renfort.id(), null), "REMPLACEMENT_DATE_PASSEE");
        refuse(() -> remplacer.executer(CENTRE, LUNDI.plusDays(3), salle, creneau, renfort.id(), null), "REMPLACEMENT_JOUR_FERME");
        remplacer.executer(CENTRE, LUNDI, salle, creneau, renfort.id(), null);
    }

    @Test
    void should_require_the_replaced_nurse_to_exist_and_to_be_absent_or_planned_on_the_case() {
        Infirmier titulaire = fiche("M1", false);
        Infirmier renfort = fiche("M2", false);
        Infirmier autre = fiche("M3", false);
        m.aujourdhui = LUNDI;
        affecter.executer(CENTRE, titulaire.id(), salle, creneau, EnumSet.of(JourDeLaSemaine.LUNDI));

        refuse(() -> remplacer.executer(CENTRE, LUNDI, salle, creneau, renfort.id(), new InfirmierId(UUID.randomUUID())),
                "INFIRMIER_INTROUVABLE");
        refuse(() -> remplacer.executer(CENTRE, LUNDI, salle, creneau, renfort.id(), autre.id()), "REMPLACEMENT_REMPLACE_NON_CONCERNE");
        declarer.executer(CENTRE, autre.id(), periode(LUNDI, LUNDI), TypeAbsence.MALADIE, null, new CompteId(UUID.randomUUID()), true);
        remplacer.executer(CENTRE, LUNDI, salle, creneau, renfort.id(), autre.id());
    }

    @Test
    void should_refuse_a_replacement_nurse_who_is_absent_planned_on_the_slot_already_replacing_deactivated_or_unknown() {
        Infirmier titulaire = fiche("M1", false);
        Infirmier absent = fiche("M2", false);
        Infirmier deja = fiche("M3", false);
        Infirmier inactif = fiche("M4", false);
        m.aujourdhui = LUNDI;
        affecter.executer(CENTRE, titulaire.id(), salle, creneau, EnumSet.of(JourDeLaSemaine.LUNDI));
        declarer.executer(CENTRE, absent.id(), periode(LUNDI, LUNDI), TypeAbsence.CONGE, null, new CompteId(UUID.randomUUID()), true);
        remplacer.executer(CENTRE, LUNDI, salle, creneau, deja.id(), null);
        m.monde.infirmiers.enregistrer(inactif.desactiver());

        refuse(() -> remplacer.executer(CENTRE, LUNDI, salle, creneau, titulaire.id(), null), "REMPLACEMENT_INELIGIBLE");
        refuse(() -> remplacer.executer(CENTRE, LUNDI, salle, creneau, absent.id(), null), "REMPLACEMENT_INELIGIBLE");
        refuse(() -> remplacer.executer(CENTRE, LUNDI, salle, creneau, deja.id(), null), "REMPLACEMENT_INELIGIBLE");
        refuse(() -> remplacer.executer(CENTRE, LUNDI, salle, creneau, inactif.id(), null), "REMPLACEMENT_INELIGIBLE");
        refuse(() -> remplacer.executer(CENTRE, LUNDI, salle, creneau, new InfirmierId(UUID.randomUUID()), null), "REMPLACEMENT_INELIGIBLE");
    }

    @Test
    void should_require_the_isolation_qualification_in_an_isolation_room() {
        Map<String, UUID> isolement = m.grille(CENTRE, true);
        SalleId salleIso = new SalleId(isolement.get("salle"));
        CreneauId creneauIso = new CreneauId(isolement.get("creneau"));
        Infirmier novice = fiche("M1", false);
        Infirmier habilite = fiche("M2", true);
        m.aujourdhui = LUNDI;

        refuse(() -> remplacer.executer(CENTRE, LUNDI, salleIso, creneauIso, novice.id(), null), "REMPLACEMENT_INELIGIBLE");
        remplacer.executer(CENTRE, LUNDI, salleIso, creneauIso, habilite.id(), null);
    }

    @Test
    void should_report_a_concurrent_write_as_such_and_cancel_a_replacement() {
        Infirmier renfort = fiche("M2", false);
        m.aujourdhui = LUNDI;
        var r = remplacer.executer(CENTRE, LUNDI, salle, creneau, renfort.id(), null);

        assertThatThrownBy(() -> m.remplacements.enregistrer(com.hemodialyse.backend.contexts.planification.domain.Remplacement.reconstituer(
                new com.hemodialyse.backend.contexts.planification.domain.Identifiants.RemplacementId(UUID.randomUUID()), CENTRE, LUNDI, salle,
                creneau, renfort.id(), null))).isInstanceOf(EcritureConcurrente.class);
        annuler.executer(CENTRE, new com.hemodialyse.backend.contexts.planification.domain.Identifiants.RemplacementId(r.id()));
        refuse(() -> annuler.executer(CENTRE, new com.hemodialyse.backend.contexts.planification.domain.Identifiants.RemplacementId(r.id())),
                "REMPLACEMENT_INTROUVABLE");
    }
}
