package com.hemodialyse.backend.domain.absence.model;

import com.hemodialyse.backend.domain.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AbsencePatientTest {

    private static final LocalDate AUJOURDHUI = LocalDate.of(2026, 10, 2);
    private static final Instant MAINTENANT = Instant.parse("2026-10-02T08:00:00Z");
    private static final UUID CENTRE = UUID.randomUUID();
    private static final UUID PATIENT = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();
    private static final ValeurAbsence VALEUR =
            ValeurAbsence.depuisHt(UUID.randomUUID(), "Forfait", new BigDecimal("10000"), new BigDecimal("19"));

    private AbsencePatient declaree(LocalDate date, MotifAbsence motif, String commentaire) {
        return AbsencePatient.declaree(CENTRE, PATIENT, date, motif, commentaire, VALEUR, USER, AUJOURDHUI, MAINTENANT);
    }

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
    void declareeSansMotifResteAQualifier() {
        assertThat(declaree(AUJOURDHUI, null, null).statut()).isEqualTo(StatutAbsence.A_QUALIFIER);
    }

    @Test
    void declareeAvecMotifJustifianteEstJustifiee() {
        AbsencePatient a = declaree(AUJOURDHUI, MotifAbsence.HOSPITALISATION, null);
        assertThat(a.statut()).isEqualTo(StatutAbsence.JUSTIFIEE);
        assertThat(a.qualifieePar()).isEqualTo(USER);
    }

    @Test
    void motifNonJustifieeDonneStatutNonJustifiee() {
        assertThat(declaree(AUJOURDHUI, MotifAbsence.NON_JUSTIFIEE, null).statut())
                .isEqualTo(StatutAbsence.NON_JUSTIFIEE);
    }

    @Test
    void dateFutureRefusee() {
        assertThatThrownBy(() -> declaree(AUJOURDHUI.plusDays(1), null, null))
                .isInstanceOf(BusinessException.class).hasMessageContaining("future");
    }

    @Test
    void dateTropAncienneRefusee() {
        assertThatThrownBy(() -> declaree(AUJOURDHUI.minusDays(AbsencePatient.DELAI_DECLARATION_JOURS + 1), null, null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void motifAutreExigeCommentaire() {
        assertThatThrownBy(() -> declaree(AUJOURDHUI, MotifAbsence.AUTRE, " "))
                .isInstanceOf(BusinessException.class);
        assertThat(declaree(AUJOURDHUI, MotifAbsence.AUTRE, "Précision").statut()).isEqualTo(StatutAbsence.JUSTIFIEE);
    }

    @Test
    void requalificationExigeCommentaire() {
        AbsencePatient a = declaree(AUJOURDHUI, MotifAbsence.MALADIE, null);
        assertThatThrownBy(() -> a.qualifier(MotifAbsence.VOYAGE, null, USER, MAINTENANT))
                .isInstanceOf(BusinessException.class);
        a.qualifier(MotifAbsence.NON_JUSTIFIEE, "Contrôle", USER, MAINTENANT);
        assertThat(a.statut()).isEqualTo(StatutAbsence.NON_JUSTIFIEE);
    }

    @Test
    void rattrapageAnnuleLaPerte() {
        AbsencePatient a = detectee();
        a.rattraper(AUJOURDHUI, USER, AUJOURDHUI, MAINTENANT);
        assertThat(a.statut()).isEqualTo(StatutAbsence.RATTRAPEE);
        assertThat(a.statut().comptabilisee()).isFalse();
        assertThat(a.dateRattrapage()).isEqualTo(AUJOURDHUI);
    }

    @Test
    void rattrapageAnterieurOuFuturRefuse() {
        AbsencePatient a = detectee();
        assertThatThrownBy(() -> a.rattraper(a.dateSeance(), USER, AUJOURDHUI, MAINTENANT))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> a.rattraper(AUJOURDHUI.plusDays(1), USER, AUJOURDHUI, MAINTENANT))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void annulationExigeCommentaireEtEstDefinitive() {
        AbsencePatient a = detectee();
        assertThatThrownBy(() -> a.annuler(null, USER, MAINTENANT)).isInstanceOf(BusinessException.class);
        a.annuler("Patient présent", USER, MAINTENANT);
        assertThat(a.statut()).isEqualTo(StatutAbsence.ANNULEE);
        assertThatThrownBy(() -> a.qualifier(MotifAbsence.MALADIE, "x", USER, MAINTENANT))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> a.annuler("encore", USER, MAINTENANT)).isInstanceOf(BusinessException.class);
    }

    @Test
    void qualificationEnRetardApresLeDelai() {
        AbsencePatient a = AbsencePatient.detectee(CENTRE, PATIENT, AUJOURDHUI.minusDays(4), VALEUR, MAINTENANT);
        assertThat(a.qualificationEnRetard(AUJOURDHUI)).isTrue();
        assertThat(detectee().qualificationEnRetard(AUJOURDHUI)).isFalse();
        a.qualifier(MotifAbsence.MALADIE, null, USER, MAINTENANT);
        assertThat(a.qualificationEnRetard(AUJOURDHUI)).isFalse();
    }
}
