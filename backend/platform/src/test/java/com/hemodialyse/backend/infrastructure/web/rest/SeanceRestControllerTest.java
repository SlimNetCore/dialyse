package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.application.notification.NotificationService;
import com.hemodialyse.backend.domain.patient.model.Patient;
import com.hemodialyse.backend.domain.patient.vo.PatientId;
import com.hemodialyse.backend.domain.seance.model.Seance;
import com.hemodialyse.backend.domain.seance.model.SeanceDetails;
import com.hemodialyse.backend.domain.seance.model.SeanceListItem;
import com.hemodialyse.backend.domain.seance.model.SeanceSearch;
import com.hemodialyse.backend.domain.seance.model.SeanceStatus;
import com.hemodialyse.backend.domain.seance.port.SeanceUseCase;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires du SeanceRestController (mock des dépendances, pas de Spring Context).
 * Pour les tests d'intégration HTTP complets (@SpringBootTest), voir SeanceIntegrationTest.
 */
class SeanceRestControllerTest {

    private static final UUID CENTER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PATIENT_ID = UUID.randomUUID();
    private static final UUID SEANCE_ID = UUID.randomUUID();
    private static final com.hemodialyse.backend.infrastructure.security.CenterAccessGuard GARDE =
            mock(com.hemodialyse.backend.infrastructure.security.CenterAccessGuard.class);

    private SeanceRestController buildController(SeanceUseCase useCase) {
        return new SeanceRestController(
                useCase,
                mock(NotificationService.class),
                mock(JdbcTemplate.class),
                GARDE,
                mock(com.hemodialyse.backend.contexts.absencespatients.publishedlanguage.AbsencesDuPilotage.class), mock(com.hemodialyse.backend.contexts.stock.publishedlanguage.ConsommationsDeSeance.class)
        );
    }

    // La liste, la création et le scan (SE1) sont servis par `contexts/seance` : leurs cas sont dans les tests du module et dans les
    // goldens `characterization/seance/se1`.

    // ─── valider ─────────────────────────────────────────────────────────────

    // SE5 : la surcharge du forfait est servie par `contexts/seance` ; ses cas sont dans les tests du module (ImposerUnForfaitTest,
    // SeancesControleurTest) et les goldens `characterization/seance/se5`.

    // SE2 : valider, signer-medecin et deverrouiller-regularisation sont servis par `contexts/seance` ; leurs cas sont dans les
    // tests du module (ValiderUneSeanceTest, SignerUneSeanceTest, DeverrouillerUneSeanceTest, SeancesControleurTest) et les goldens
    // `characterization/seance/se2`.

    // L'isolation multi-centre du détail (SE1) est tenue par `contexts/seance` (ConsulterUneSeanceTest) et les goldens SE1.
}




