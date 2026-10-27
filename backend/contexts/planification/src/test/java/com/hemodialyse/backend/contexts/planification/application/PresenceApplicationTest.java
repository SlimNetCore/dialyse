package com.hemodialyse.backend.contexts.planification.application;

import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CreneauId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.SalleId;
import com.hemodialyse.backend.contexts.planification.domain.Pagination;
import com.hemodialyse.backend.contexts.planification.domain.PaginationInvalide;
import com.hemodialyse.backend.contexts.planification.domain.Presence.AbsenceConnue;
import com.hemodialyse.backend.contexts.planification.domain.Presence.AffectationPrevue;
import com.hemodialyse.backend.contexts.planification.domain.Presence.InfirmierDeLaPresence;
import com.hemodialyse.backend.contexts.planification.domain.Presence.Personnel;
import com.hemodialyse.backend.contexts.planification.domain.Qualification;
import com.hemodialyse.backend.contexts.planification.domain.RatioDeLaPresence;
import com.hemodialyse.backend.contexts.planification.domain.RegleDePlanificationViolee;
import com.hemodialyse.backend.contexts.planification.domain.ReglagesDePlanification;
import com.hemodialyse.backend.contexts.planification.domain.ReglagesRepository;
import com.hemodialyse.backend.contexts.planification.domain.SaisieInvalide;
import com.hemodialyse.backend.contexts.planification.domain.TypeAbsence;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.ImpressionDeLaPresence;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.NotificationsDeLaPresence;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.PlanningDesPlaces;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cas d'usage de la présence avec des doubles en mémoire : lectures, validation de la case des remplaçants, pagination de la charge,
 * formats d'impression et contrôle de 07:15 (centres actifs, clé du jour, isolation des échecs).
 */
class PresenceApplicationTest {
    static final CentreId A = new CentreId(UUID.randomUUID());
    static final CentreId B = new CentreId(UUID.randomUUID());
    static final UUID SALLE = UUID.randomUUID();
    static final UUID CRENEAU = UUID.randomUUID();
    static final LocalDate AUJOURDHUI = LocalDate.of(2026, 10, 27);   // mardi

    final List<InfirmierDeLaPresence> infirmiers = new ArrayList<>();
    final List<AffectationPrevue> affectations = new ArrayList<>();
    final List<AbsenceConnue> absences = new ArrayList<>();
    int ratio = 4;
    int patients = 0;
    Set<LocalDate> fermes = Set.of();
    RuntimeException panne;
    CentreId centrePanne;
    final List<CentreId> actifs = new ArrayList<>(List.of(A));
    final List<String> envois = new ArrayList<>();
    boolean dejaEmis = false;
    ReglagesDePlanification reglagesDuCentre = ReglagesDePlanification.parDefaut();

    final HorlogeDesCentres horloge = centre -> AUJOURDHUI;
    final PersonnelDeLaPresence personnel = (centre, du, au) -> {
        if (panne != null && centre.equals(centrePanne)) throw panne;
        return new Personnel(infirmiers, affectations, absences, List.of(), RatioDeLaPresence.depuisLaBase(ratio));
    };
    final PlanningDesPlaces places = (centre, du, au) -> {
        List<PlanningDesPlaces.Occupation> occupations = new ArrayList<>();
        for (int i = 0; i < patients; i++) {
            occupations.add(new PlanningDesPlaces.Occupation(SALLE, CRENEAU, EnumSet.allOf(DayOfWeek.class), null, null));
        }
        return new PlanningDesPlaces.Planning(List.of(new PlanningDesPlaces.Salle(SALLE, "Salle 1", false)),
                List.of(new PlanningDesPlaces.Creneau(CRENEAU, "Matin", 1)), occupations, Set.of(),
                fermes.stream().map(d -> new PlanningDesPlaces.Fermeture(d, "Férié")).toList());
    };
    final ReglagesRepository reglages = new ReglagesRepository() {
        @Override
        public ReglagesDePlanification lire(CentreId centre) {
            return reglagesDuCentre;
        }

        @Override
        public void enregistrer(CentreId centre, ReglagesDePlanification r) {
        }

        @Override
        public List<CentreId> centresEnReplanificationNocturne() {
            return List.of();
        }
    };
    final NotificationsDeLaPresence notifications = new NotificationsDeLaPresence() {
        @Override
        public boolean sousEffectif(UUID centre, LocalDate jour, int nbCases, LocalDate premiereDate) {
            envois.add("sous " + nbCases + " " + jour + " " + premiereDate);
            return !dejaEmis;
        }

        @Override
        public boolean sureffectif(UUID centre, LocalDate jour, int nbCases, int nbVacations, int heures, LocalDate premiereDate) {
            envois.add("sur " + nbCases + " " + nbVacations + " " + heures);
            return !dejaEmis;
        }
    };

    @BeforeEach
    void unInfirmier() {
        infirmiers.add(new InfirmierDeLaPresence(UUID.randomUUID(), "Amrani", "Sara", Qualification.INFIRMIER, false));
    }

    @Test
    void should_refuse_an_alert_horizon_outside_one_to_sixty_days() {
        ConsulterLaPresence presence = new ConsulterLaPresence(personnel, places, horloge);
        assertThatThrownBy(() -> presence.alertes(A, 0)).isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> presence.alertes(A, 61)).isInstanceOf(SaisieInvalide.class);
        assertThat(presence.alertes(A, 60)).isEmpty();
    }

    @Test
    void should_read_today_at_the_center_when_no_date_is_given() {
        ConsulterLaPresence presence = new ConsulterLaPresence(personnel, places, horloge);
        assertThat(presence.semaine(A, null).debut()).isEqualTo(LocalDate.of(2026, 10, 25));
        assertThat(presence.semaine(A, LocalDate.of(2026, 11, 3)).debut()).isEqualTo(LocalDate.of(2026, 11, 1));
    }

    @Test
    void should_expose_surplus_and_double_assignments_in_the_surplus_alerts() {
        affectations.add(new AffectationPrevue(infirmiers.getFirst().id(), SALLE, CRENEAU, EnumSet.allOf(
                com.hemodialyse.backend.contexts.planification.domain.JourDeLaSemaine.class)));
        ConsulterLaPresence presence = new ConsulterLaPresence(personnel, places, horloge);
        var alertes = presence.sureffectifs(A, 1);
        assertThat(alertes.sureffectifs()).hasSize(1);
        assertThat(alertes.doublesAffectations()).isEmpty();
    }

    @Test
    void should_warn_the_rotation_about_the_days_of_the_week_in_surplus_in_week_order() {
        affectations.add(new AffectationPrevue(infirmiers.getFirst().id(), SALLE, CRENEAU,
                EnumSet.allOf(com.hemodialyse.backend.contexts.planification.domain.JourDeLaSemaine.class)));
        SurEffectifCalculeParLaPresence surEffectif = new SurEffectifCalculeParLaPresence(personnel, places);

        assertThat(surEffectif.joursEnSureffectif(A.valeur(), SALLE, CRENEAU, Set.of("MARDI", "LUNDI", "JEUDI"), AUJOURDHUI))
                .containsExactly("LUNDI", "MARDI", "JEUDI");
        assertThat(surEffectif.joursEnSureffectif(A.valeur(), SALLE, CRENEAU, Set.of("MARDI"), AUJOURDHUI)).containsExactly("MARDI");
        assertThat(surEffectif.joursEnSureffectif(A.valeur(), UUID.randomUUID(), CRENEAU, Set.of("MARDI"), AUJOURDHUI)).isEmpty();
        patients = 4;
        assertThat(surEffectif.joursEnSureffectif(A.valeur(), SALLE, CRENEAU, Set.of("MARDI"), AUJOURDHUI)).isEmpty();
    }

    @Test
    void should_validate_the_case_of_the_replacement_candidates() {
        SallesDuCentre salles = new SallesDuCentre() {
            @Override
            public Set<SalleId> toutes(CentreId centre) {
                return Set.of(new SalleId(SALLE));
            }

            @Override
            public Set<SalleId> sallesDIsolement(CentreId centre) {
                return Set.of();
            }

            @Override
            public void marquerIsolement(CentreId centre, Set<SalleId> s) {
            }
        };
        CreneauxDuCentre creneaux = (centre, creneau) -> creneau.valeur().equals(CRENEAU);
        ClasserLesRemplacants classer = new ClasserLesRemplacants(personnel, places, salles, creneaux,
                (centre, jour) -> !fermes.contains(jour), horloge);
        LocalDate demain = AUJOURDHUI.plusDays(1);

        assertThat(classer.executer(A, demain, new SalleId(SALLE), new CreneauId(CRENEAU))).hasSize(1);
        assertThatThrownBy(() -> classer.executer(A, demain, new SalleId(UUID.randomUUID()), new CreneauId(CRENEAU)))
                .isInstanceOfSatisfying(RegleDePlanificationViolee.class, e -> assertThat(e.code()).isEqualTo("AFFECTATION_SALLE_INCONNUE"));
        assertThatThrownBy(() -> classer.executer(A, demain, new SalleId(SALLE), new CreneauId(UUID.randomUUID())))
                .isInstanceOfSatisfying(RegleDePlanificationViolee.class, e -> assertThat(e.code()).isEqualTo("AFFECTATION_CRENEAU_INCONNU"));
        assertThatThrownBy(() -> classer.executer(A, AUJOURDHUI.minusDays(1), new SalleId(SALLE), new CreneauId(CRENEAU)))
                .isInstanceOfSatisfying(RegleDePlanificationViolee.class, e -> assertThat(e.code()).isEqualTo("REMPLACEMENT_DATE_PASSEE"));
        fermes = Set.of(demain);
        assertThatThrownBy(() -> classer.executer(A, demain, new SalleId(SALLE), new CreneauId(CRENEAU)))
                .isInstanceOfSatisfying(RegleDePlanificationViolee.class, e -> assertThat(e.code()).isEqualTo("REMPLACEMENT_JOUR_FERME"));
    }

    @Test
    void should_paginate_the_workload_cap_the_size_and_return_the_effective_size() {
        for (int i = 0; i < 4; i++) {
            infirmiers.add(new InfirmierDeLaPresence(UUID.randomUUID(), "Nom" + i, "Prenom", Qualification.INFIRMIER, false));
        }
        ConsulterLaChargeDesInfirmiers charge = new ConsulterLaChargeDesInfirmiers(personnel, places, horloge);
        var page = charge.executer(A, YearMonth.of(2026, 11), new Pagination(1, 2));
        assertThat(page.page().elements()).hasSize(2);
        assertThat(page.page().total()).isEqualTo(5);
        assertThat(charge.executer(A, null, new Pagination(0, 1000)).page().taille()).isEqualTo(100);
        assertThat(charge.executer(A, null, new Pagination(9, 20)).page().elements()).isEmpty();
        assertThatThrownBy(() -> new Pagination(-1, 20)).isInstanceOf(PaginationInvalide.class);
    }

    @Test
    void should_serve_pdf_html_and_excel_and_refuse_every_other_print_format() {
        for (String format : List.of("PDF", "pdf", "HTML", "EXCEL", "XLS", "XLSX")) {
            ImprimerLaPresence imprimer = new ImprimerLaPresence((centre, debut) -> new ImpressionDeLaPresence.Document(new byte[]{1}, format),
                    horloge);
            assertThat(imprimer.executer(A, AUJOURDHUI).contenu()).hasSize(1);
        }
        assertThat(new ImprimerLaPresence((centre, debut) -> new ImpressionDeLaPresence.Document(new byte[]{1}, null), horloge)
                .executer(A, null).format()).isEqualTo(ImprimerLaPresence.Format.PDF);
        for (String format : List.of("CSV", "RTF", "inconnu")) {
            ImprimerLaPresence imprimer = new ImprimerLaPresence((centre, debut) -> new ImpressionDeLaPresence.Document(new byte[0], format),
                    horloge);
            assertThatThrownBy(() -> imprimer.executer(A, null)).isInstanceOf(SaisieInvalide.class);
        }
        ImprimerLaPresence dimanche = new ImprimerLaPresence((centre, debut) -> {
            assertThat(debut).isEqualTo(LocalDate.of(2026, 10, 25));
            return new ImpressionDeLaPresence.Document(new byte[]{1}, "PDF");
        }, horloge);
        assertThat(dimanche.executer(A, null).debutDeSemaine()).isEqualTo(LocalDate.of(2026, 10, 25));
    }

    // ------------------------------------------------------------------ contrôle de 07:15

    private ControlerLaPresence job() {
        return new ControlerLaPresence(() -> actifs, horloge, personnel, places, reglages, notifications);
    }

    @Test
    void should_notify_understaffing_with_the_count_and_the_first_date() {
        patients = 5;
        var resultat = job().controler().centres().getFirst();
        assertThat(resultat.sousEffectif()).isEqualTo(14);
        assertThat(resultat.sousEffectifNotifie()).isTrue();
        assertThat(envois).containsExactly("sous 14 2026-10-27 2026-10-27");
    }

    @Test
    void should_notify_surplus_with_vacations_and_hours_of_the_center_setting() {
        affectations.add(new AffectationPrevue(infirmiers.getFirst().id(), SALLE, CRENEAU,
                EnumSet.allOf(com.hemodialyse.backend.contexts.planification.domain.JourDeLaSemaine.class)));
        var resultat = job().controler().centres().getFirst();
        assertThat(resultat.sureffectif()).isEqualTo(14);
        assertThat(envois).containsExactly("sur 14 14 " + 14 * ReglagesDePlanification.parDefaut().heuresParVacation());
    }

    @Test
    void should_control_every_active_center_even_without_nurse_and_report_when_the_alert_was_already_sent() {
        infirmiers.clear();
        patients = 8;
        actifs.add(B);
        dejaEmis = true;
        var resultats = job().controler().centres();
        assertThat(resultats).hasSize(2);
        assertThat(resultats).allSatisfy(r -> {
            assertThat(r.sousEffectif()).isEqualTo(14);
            assertThat(r.sousEffectifNotifie()).isFalse();
        });
    }

    @Test
    void should_not_control_centers_that_are_not_listed_as_active() {
        patients = 8;
        actifs.clear();
        assertThat(job().controler().centres()).isEmpty();
        assertThat(envois).isEmpty();
    }

    @Test
    void should_report_the_failure_of_the_list_of_active_centers_without_failing_the_control() {
        var bilan = new ControlerLaPresence(() -> {
            throw new IllegalStateException("base indisponible");
        }, horloge, personnel, places, reglages, notifications).controler();
        assertThat(bilan.centres()).isEmpty();
        assertThat(bilan.incident()).contains("liste des centres actifs").contains("base indisponible");
        assertThat(envois).isEmpty();
    }

    @Test
    void should_keep_controlling_the_other_centers_when_one_fails() {
        patients = 5;
        actifs.add(B);
        panne = new IllegalStateException("base indisponible");
        centrePanne = A;
        var resultats = job().controler().centres();
        assertThat(resultats.get(0).incidents()).hasSize(1).first().asString().contains("base indisponible");
        assertThat(resultats.get(0).sousEffectifNotifie()).isFalse();
        assertThat(resultats.get(1).incidents()).isEmpty();
        assertThat(resultats.get(1).sousEffectifNotifie()).isTrue();
    }

    @Test
    void should_keep_the_other_control_when_the_notification_of_one_fails() {
        patients = 5;
        NotificationsDeLaPresence cassee = new NotificationsDeLaPresence() {
            @Override
            public boolean sousEffectif(UUID centre, LocalDate jour, int nbCases, LocalDate premiereDate) {
                throw new IllegalStateException("diffusion impossible");
            }

            @Override
            public boolean sureffectif(UUID centre, LocalDate jour, int nbCases, int nbVacations, int heures, LocalDate premiereDate) {
                envois.add("sur");
                return true;
            }
        };
        var r = new ControlerLaPresence(() -> actifs, horloge, personnel, places, reglages, cassee).controler().centres().getFirst();
        assertThat(r.incidents()).hasSize(1);
        assertThat(r.sousEffectifNotifie()).isFalse();
        assertThat(r.sureffectif()).isZero();
    }

    @Test
    void should_not_signal_understaffing_when_the_only_nurse_of_a_covered_case_is_present() {
        patients = 4;
        affectations.add(new AffectationPrevue(infirmiers.getFirst().id(), SALLE, CRENEAU,
                EnumSet.allOf(com.hemodialyse.backend.contexts.planification.domain.JourDeLaSemaine.class)));
        absences.add(new AbsenceConnue(UUID.randomUUID(), AUJOURDHUI, AUJOURDHUI, TypeAbsence.CONGE));
        var r = job().controler().centres().getFirst();
        assertThat(r.sousEffectif()).isZero();
        assertThat(envois).isEmpty();
    }
}
