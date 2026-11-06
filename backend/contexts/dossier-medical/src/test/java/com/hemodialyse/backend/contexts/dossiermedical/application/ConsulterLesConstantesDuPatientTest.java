package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.contrat.HistoriqueDesConstantesEnMemoire;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Constantes;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PageDeConstantes;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.SaisieInvalide;
import com.hemodialyse.backend.contexts.dossiermedical.domain.SeanceId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConsulterLesConstantesDuPatientTest {

    private static final CentreId CENTRE = new CentreId(new UUID(0, 0xC));
    private static final CentreId AUTRE_CENTRE = new CentreId(new UUID(0, 0xD));
    private static final UUID PATIENT = new UUID(1, 1);
    private static final SeanceId SEANCE = new SeanceId(new UUID(2, 1));

    /** Double de session : le centre du jeton, 403 pour un autre centre ou une session sans centre. */
    private static final class SessionFixe implements SessionCourante {
        CentreId centreDeLaSession = CENTRE;

        @Override
        public CentreId centre(UUID centreDemande) {
            if (centreDeLaSession == null) throw new AccesRefuse();
            if (centreDemande != null && !centreDemande.equals(centreDeLaSession.valeur())) throw new AccesRefuse();
            return centreDeLaSession;
        }
    }

    private SessionFixe session;
    private HistoriqueDesConstantesEnMemoire historique;
    private ConsulterLesConstantesDuPatient consulter;

    @BeforeEach
    void preparer() {
        session = new SessionFixe();
        historique = new HistoriqueDesConstantesEnMemoire();
        consulter = new ConsulterLesConstantesDuPatient(session, historique);
        historique.seance(CENTRE, new PatientId(PATIENT), SEANCE, LocalDate.of(2026, 6, 1), Instant.parse("2026-06-01T08:00:00Z"));
        historique.volet(CENTRE, SEANCE, new Constantes(new BigDecimal("70.50"), null, "130/80", null, null, null, 240));
    }

    @Test
    void should_return_the_history_of_the_session_centre_when_no_centre_is_requested() {
        PageDeConstantes page = consulter.executer(null, PATIENT, 0, 20);

        assertThat(page.items()).hasSize(1);
        assertThat(page.items().get(0).constantes().tensionAvant()).isEqualTo("130/80");
        assertThat(page.total()).isEqualTo(1);
    }

    @Test
    void should_accept_the_requested_centre_when_it_is_the_session_centre() {
        assertThat(consulter.executer(CENTRE.valeur(), PATIENT, 0, 20).items()).hasSize(1);
    }

    @Test
    void should_deny_access_to_the_history_of_another_centre() {
        assertThatThrownBy(() -> consulter.executer(AUTRE_CENTRE.valeur(), PATIENT, 0, 20)).isInstanceOf(AccesRefuse.class);
    }

    @Test
    void should_deny_access_without_a_session_centre() {
        session.centreDeLaSession = null;
        assertThatThrownBy(() -> consulter.executer(null, PATIENT, 0, 20)).isInstanceOf(AccesRefuse.class);
    }

    @Test
    void should_read_only_the_history_of_the_session_centre_whatever_the_patient() {
        session.centreDeLaSession = AUTRE_CENTRE;
        PageDeConstantes page = consulter.executer(null, PATIENT, 0, 20);
        assertThat(page.items()).isEmpty();
        assertThat(page.total()).isZero();
    }

    @Test
    void should_return_an_empty_history_for_an_unknown_patient() {
        assertThat(consulter.executer(null, new UUID(9, 9), 0, 20).items()).isEmpty();
    }

    @Test
    void should_reject_an_unreadable_request_before_reading() {
        assertThatThrownBy(() -> consulter.executer(null, null, 0, 20)).isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> consulter.executer(null, PATIENT, -1, 20)).isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> consulter.executer(null, PATIENT, 0, 0)).isInstanceOf(SaisieInvalide.class);
    }

    @Test
    void should_check_the_centre_before_the_request_values() {
        assertThatThrownBy(() -> consulter.executer(AUTRE_CENTRE.valeur(), null, -1, 0)).isInstanceOf(AccesRefuse.class);
    }
}
