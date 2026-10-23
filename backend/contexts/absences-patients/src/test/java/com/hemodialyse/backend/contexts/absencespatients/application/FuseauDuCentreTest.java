package com.hemodialyse.backend.contexts.absencespatients.application;

import com.hemodialyse.backend.contexts.absencespatients.doubles.AbsencesEnMemoire;
import com.hemodialyse.backend.contexts.absencespatients.doubles.SuiviEnMemoire;
import com.hemodialyse.backend.contexts.absencespatients.doubles.UniteDeTravailEnMemoire;
import com.hemodialyse.backend.contexts.absencespatients.domain.DroitDeCorrection;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.AbsenceId;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.PatientId;
import com.hemodialyse.backend.contexts.absencespatients.domain.RegleDAbsenceViolee;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Décision 7 : « aujourd'hui » est la date du fuseau du centre, pas celle d'UTC (le centre est à Alger, UTC+1).
 */
class FuseauDuCentreTest {

    private static final CentreId ALGER = new CentreId(new UUID(10, 1));
    private static final CentreId LONDRES = new CentreId(new UUID(10, 2));
    private static final PatientId PATIENT = new PatientId(new UUID(11, 1));
    /** 21 octobre 23:30 UTC = 22 octobre 00:30 à Alger. */
    private static final Instant ENTRE_MINUIT_ET_UNE_HEURE = Instant.parse("2026-10-21T23:30:00Z");
    private static final HorlogeDesCentres HORLOGE = new HorlogeDesCentres(Clock.fixed(ENTRE_MINUIT_ET_UNE_HEURE, ZoneOffset.UTC),
            centre -> centre.equals(ALGER) ? ZoneId.of("Africa/Algiers") : ZoneOffset.UTC);

    @Test
    void aujourd_hui_suit_le_fuseau_du_centre_et_l_instant_reste_absolu() {
        assertThat(HORLOGE.aujourdhui(ALGER)).isEqualTo(LocalDate.of(2026, 10, 22));
        assertThat(HORLOGE.aujourdhui(LONDRES)).isEqualTo(LocalDate.of(2026, 10, 21));
        assertThat(HORLOGE.maintenant()).isEqualTo(ENTRE_MINUIT_ET_UNE_HEURE);
    }

    @Test
    void une_absence_du_jour_se_declare_entre_minuit_et_une_heure_a_alger_mais_pas_encore_a_la_date_utc() {
        AbsencesEnMemoire absences = new AbsencesEnMemoire();
        var declarer = new DeclarerUneAbsence(new UniteDeTravailEnMemoire(absences), (centre, patient) -> true, absences,
                (centre, patient, jour) -> false, new SuiviEnMemoire(), (centre, patient, jour) -> Optional.empty(),
                (centre, jour) -> BigDecimal.ZERO, () -> new AbsenceId(UUID.randomUUID()), (c, p, j) -> { }, HORLOGE);
        var commande = new DeclarerUneAbsence.Commande(PATIENT, LocalDate.of(2026, 10, 22), null, null);
        var intervenant = new Intervenant(null, DroitDeCorrection.REFUSE);

        assertThat(declarer.executer(ALGER, commande, intervenant).absence().dateDeSeance()).isEqualTo(LocalDate.of(2026, 10, 22));
        assertThatThrownBy(() -> declarer.executer(LONDRES, commande, intervenant)).isInstanceOfSatisfying(RegleDAbsenceViolee.class,
                e -> assertThat(e.code()).isEqualTo("ABSENCE_DATE_FUTURE"));
    }

    @Test
    void la_semaine_courante_et_le_retard_suivent_le_fuseau_du_centre() {
        SuiviEnMemoire suivi = new SuiviEnMemoire();
        // samedi 24 octobre 23:30 UTC = dimanche 25 à Alger : la semaine du planning change à Alger avant UTC
        HorlogeDesCentres dimancheSoir = new HorlogeDesCentres(
                Clock.fixed(Instant.parse("2026-10-24T23:30:00Z"), ZoneOffset.UTC),
                centre -> centre.equals(ALGER) ? ZoneId.of("Africa/Algiers") : ZoneOffset.UTC);
        suivi.poserLaSeance(ALGER, PATIENT, LocalDate.of(2026, 10, 24), "VALIDEE");
        suivi.poserLaSeance(ALGER, PATIENT, LocalDate.of(2026, 10, 25), "VALIDEE");
        var suivre = new SuivreLaSemaine(suivi, suivi, dimancheSoir);

        assertThat(suivre.executer(ALGER, null).seancesRealisees()).extracting(s -> s.date())
                .containsExactly(LocalDate.of(2026, 10, 25));
    }
}
