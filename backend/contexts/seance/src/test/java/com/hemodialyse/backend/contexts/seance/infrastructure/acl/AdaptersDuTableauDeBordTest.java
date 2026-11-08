package com.hemodialyse.backend.contexts.seance.infrastructure.acl;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.hemodialyse.backend.contexts.absencespatients.publishedlanguage.AbsencesDuPilotage;
import com.hemodialyse.backend.contexts.seance.contrat.CalendrierDuCentreEnMemoire;
import com.hemodialyse.backend.contexts.seance.domain.CalendrierDuCentre;
import com.hemodialyse.backend.contexts.seance.domain.JourDuCalendrier;
import com.hemodialyse.backend.contexts.seance.domain.MoisDuCalendrier;
import com.hemodialyse.backend.contexts.seance.domain.MoisDuTableauDeBord;
import com.hemodialyse.backend.contexts.seance.domain.NatureDeJour;
import com.hemodialyse.backend.contexts.seance.infrastructure.acl.absencespatients.AbsencesDuCentreParLePilotage;
import com.hemodialyse.backend.contexts.seance.infrastructure.persistence.JoursBloquesDuCalendrier;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** SE7b : traduction des absences du langage publié, et lecture tolérante des jours non ouvrés (WARN avec le seul identifiant du centre). */
class AdaptersDuTableauDeBordTest {

    private static final CentreId A = new CentreId(new UUID(0, 0xA));
    private static final UUID PATIENT = new UUID(1, 1);
    private static final LocalDate DU = LocalDate.of(2026, 1, 1);
    private static final LocalDate AU = LocalDate.of(2026, 1, 31);

    /** Langage publié des Absences, double : une absence, et les bornes demandées. */
    private static final class PilotageDouble implements AbsencesDuPilotage {
        LocalDate du;
        LocalDate au;
        UUID centre;

        @Override
        public LocalDate aujourdhui() {
            throw new AssertionError("le jour vient de l'horloge du centre, pas du pilotage");
        }

        @Override
        public long nombreHorsAnnulees(UUID centre, LocalDate du, LocalDate au) {
            this.centre = centre;
            this.du = du;
            this.au = au;
            return 4L;
        }

        @Override
        public List<AbsenceDuCentre> horsAnnulees(UUID centre, LocalDate du, LocalDate au) {
            this.centre = centre;
            this.du = du;
            this.au = au;
            return List.of(new AbsenceDuCentre(PATIENT, LocalDate.of(2026, 1, 9), "A_QUALIFIER"));
        }

        @Override
        public List<CumulDuCentre> cumulParCentre(Collection<UUID> centres, LocalDate du, LocalDate au) {
            throw new AssertionError();
        }

        @Override
        public List<AbsencesDuMois> mensuel(Collection<UUID> centres, LocalDate du, LocalDate au) {
            throw new AssertionError();
        }
    }

    @Test
    void should_translate_the_published_absences_and_pass_the_centre_and_the_bounds_unchanged() {
        var pilotage = new PilotageDouble();
        var acl = new AbsencesDuCentreParLePilotage(pilotage);

        assertThat(acl.nombre(A, DU, AU)).isEqualTo(4);
        var liste = acl.liste(A, DU, AU);

        assertThat(pilotage.centre).isEqualTo(A.valeur());
        assertThat(pilotage.du).isEqualTo(DU);
        assertThat(pilotage.au).isEqualTo(AU);
        assertThat(liste).singleElement().satisfies(a -> {
            assertThat(a.patient().valeur()).isEqualTo(PATIENT);
            assertThat(a.jour()).isEqualTo(LocalDate.of(2026, 1, 9));
            assertThat(a.statut()).isEqualTo("A_QUALIFIER");
        });
    }

    @Test
    void should_merge_holidays_and_closures_of_the_month_without_duplicates() {
        var calendrier = new CalendrierDuCentreEnMemoire();
        calendrier.ajouter(new JourDuCalendrier(new UUID(3, 1), A, LocalDate.of(2026, 1, 1), NatureDeJour.JOUR_FERIE, "Férié"));
        calendrier.ajouter(new JourDuCalendrier(new UUID(3, 2), A, LocalDate.of(2026, 1, 1), NatureDeJour.FERMETURE_EXCEPTIONNELLE, "Double"));
        calendrier.ajouter(new JourDuCalendrier(new UUID(3, 3), A, LocalDate.of(2026, 1, 20), NatureDeJour.FERMETURE_EXCEPTIONNELLE, "Travaux"));
        calendrier.ajouter(new JourDuCalendrier(new UUID(3, 4), A, LocalDate.of(2026, 2, 1), NatureDeJour.JOUR_FERIE, "Hors mois"));

        assertThat(new JoursBloquesDuCalendrier(calendrier).du(A, new MoisDuTableauDeBord(2026, 1)))
                .containsExactlyInAnyOrder(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 20));
    }

    @Test
    void should_swallow_a_failing_calendar_but_log_a_warning_with_the_centre_only() {
        CalendrierDuCentre casse = new CalendrierDuCentre() {
            @Override
            public List<JourDuCalendrier> duMois(CentreId centre, NatureDeJour nature, MoisDuCalendrier mois) {
                if (nature == NatureDeJour.JOUR_FERIE) throw new IllegalStateException("relation \"center_holiday\" does not exist; secret=42");
                return List.of(new JourDuCalendrier(new UUID(3, 3), centre, LocalDate.of(2026, 1, 20), nature, "Travaux"));
            }

            @Override
            public void ajouter(JourDuCalendrier jour) {
                throw new AssertionError();
            }

            @Override
            public boolean retirer(CentreId centre, NatureDeJour nature, UUID id) {
                throw new AssertionError();
            }
        };
        Logger journal = (Logger) LoggerFactory.getLogger(JoursBloquesDuCalendrier.class);
        ListAppender<ILoggingEvent> evenements = new ListAppender<>();
        evenements.start();
        journal.addAppender(evenements);
        try {
            var jours = new JoursBloquesDuCalendrier(casse).du(A, new MoisDuTableauDeBord(2026, 1));

            // comportement observable du legacy : l'échec d'une table n'empêche ni l'autre table ni le tableau de bord
            assertThat(jours).containsExactly(LocalDate.of(2026, 1, 20));
            assertThat(evenements.list).hasSize(1);
            String message = evenements.list.get(0).getFormattedMessage();
            assertThat(evenements.list.get(0).getLevel().toString()).isEqualTo("WARN");
            assertThat(message).contains(A.valeur().toString()).doesNotContain("secret").doesNotContain("center_holiday");
            assertThat(evenements.list.get(0).getThrowableProxy()).isNull();
        } finally {
            journal.detachAppender(evenements);
        }
    }
}
