package com.hemodialyse.backend.domain.absence.model;

import com.hemodialyse.backend.domain.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Ce qui reste de l'absence legacy pour la détection de nuit et la réconciliation (slice A3) : absence détectée « à qualifier »,
 * annulation avec commentaire, retard de qualification. Le cycle de vie complet est testé dans le contexte absences-patients (A2).
 */
class AbsencePatientTest {

    private static final LocalDate AUJOURDHUI = LocalDate.of(2026, 10, 2);
    private static final Instant MAINTENANT = Instant.parse("2026-10-02T08:00:00Z");
    private static final UUID CENTRE = UUID.randomUUID();
    private static final UUID PATIENT = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();
    private static final ValeurAbsence VALEUR =
            ValeurAbsence.depuisHt(UUID.randomUUID(), "Forfait", new BigDecimal("10000"), new BigDecimal("19"));

    private AbsencePatient detectee() {
        return AbsencePatient.detectee(CENTRE, PATIENT, AUJOURDHUI.minusDays(1), VALEUR, MAINTENANT);
    }

    @Test
    void detecteeEstAQualifier() {
        AbsencePatient a = detectee();
        assertThat(a.statut()).isEqualTo(StatutAbsence.A_QUALIFIER);
        assertThat(a.source()).isEqualTo(SourceAbsence.AUTOMATIQUE);
        assertThat(a.statut().comptabilisee()).isTrue();
    }

    @Test
    void annulationExigeCommentaireEtEstDefinitive() {
        AbsencePatient a = detectee();
        assertThatThrownBy(() -> a.annuler(null, USER, MAINTENANT)).isInstanceOf(BusinessException.class);
        a.annuler("Patient présent", USER, MAINTENANT);
        assertThat(a.statut()).isEqualTo(StatutAbsence.ANNULEE);
        assertThat(a.commentaire()).isEqualTo("Patient présent");
        assertThatThrownBy(() -> a.annuler("encore", USER, MAINTENANT)).isInstanceOf(BusinessException.class);
    }
}
