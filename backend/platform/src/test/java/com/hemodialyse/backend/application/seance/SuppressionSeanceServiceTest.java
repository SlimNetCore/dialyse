package com.hemodialyse.backend.application.seance;

import com.hemodialyse.backend.domain.medical.anemie.port.AdministrationTraitementRepositoryPort;
import com.hemodialyse.backend.domain.seance.model.Seance;
import com.hemodialyse.backend.domain.seance.model.SeanceStatus;
import com.hemodialyse.backend.domain.seance.model.SuppressionSeance;
import com.hemodialyse.backend.domain.seance.port.SeanceRepositoryPort;
import com.hemodialyse.backend.domain.seance.port.SuppressionSeanceJournalPort;
import com.hemodialyse.backend.domain.seance.port.VoletMedicalRepositoryPort;
import com.hemodialyse.backend.domain.seance.port.VoletParamedicalRepositoryPort;
import com.hemodialyse.backend.domain.shared.exception.BusinessException;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.domain.stock.port.BonSortieUseCase;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SuppressionSeanceServiceTest {

    private static final CenterId CENTRE = CenterId.of(UUID.randomUUID());
    private static final Instant T0 = Instant.parse("2026-10-06T10:00:00Z");

    private final SeanceRepositoryPort seances = mock(SeanceRepositoryPort.class);
    private final VoletParamedicalRepositoryPort voletsP = mock(VoletParamedicalRepositoryPort.class);
    private final VoletMedicalRepositoryPort voletsM = mock(VoletMedicalRepositoryPort.class);
    private final AdministrationTraitementRepositoryPort administrations = mock(AdministrationTraitementRepositoryPort.class);
    private final BonSortieUseCase sorties = mock(BonSortieUseCase.class);
    private final SuppressionSeanceJournalPort journal = mock(SuppressionSeanceJournalPort.class);
    private final SuppressionSeanceService service = new SuppressionSeanceService(seances, voletsP, voletsM,
            administrations, sorties, journal, Clock.fixed(T0, ZoneOffset.UTC));

    private Seance seance(SeanceStatus statut) {
        Seance s = new Seance(UUID.randomUUID(), UUID.randomUUID(), CENTRE.value(), LocalDate.of(2026, 10, 5));
        s.setStatus(statut);
        when(seances.findById(s.getId(), CENTRE)).thenReturn(Optional.of(s));
        return s;
    }

    @Test
    void restores_the_stock_then_deletes_the_linked_data_the_session_and_journals_it() {
        Seance s = seance(SeanceStatus.VALIDEE);

        SuppressionSeance trace = service.supprimer(CENTRE, s.getId(), "Séance saisie sur le mauvais patient", "admin");

        InOrder ordre = inOrder(sorties, administrations, voletsP, voletsM, seances, journal);
        ordre.verify(sorties).annulerSortiesSeance(CENTRE, s.getId(), "admin");
        ordre.verify(administrations).deleteBySeanceId(s.getId(), CENTRE);
        ordre.verify(voletsP).deleteBySeanceId(s.getId(), CENTRE);
        ordre.verify(voletsM).deleteBySeanceId(s.getId(), CENTRE);
        ordre.verify(seances).delete(CENTRE, s.getId());
        ArgumentCaptor<SuppressionSeance> journalisee = ArgumentCaptor.forClass(SuppressionSeance.class);
        ordre.verify(journal).enregistrer(journalisee.capture());
        assertThat(journalisee.getValue()).isEqualTo(trace);
        assertThat(trace.supprimeLe()).isEqualTo(T0);
        assertThat(trace.statut()).isEqualTo(SeanceStatus.VALIDEE);
    }

    @Test
    void refuses_a_session_of_another_center_a_billed_session_and_a_missing_reason_without_touching_anything() {
        assertThat(catchThrowableOfType(BusinessException.class,
                () -> service.supprimer(CENTRE, UUID.randomUUID(), "Doublon", "admin")).getCode())
                .isEqualTo("SEANCE_INTROUVABLE");
        Seance facturee = seance(SeanceStatus.FACTUREE);
        assertThat(catchThrowableOfType(BusinessException.class,
                () -> service.supprimer(CENTRE, facturee.getId(), "Doublon", "admin")).getCode())
                .isEqualTo("SEANCE_FACTUREE_NON_SUPPRIMABLE");
        Seance creee = seance(SeanceStatus.CREE);
        assertThat(catchThrowableOfType(BusinessException.class,
                () -> service.supprimer(CENTRE, creee.getId(), " ", "admin")).getCode())
                .isEqualTo("SEANCE_SUPPRESSION_MOTIF_INVALIDE");

        verifyNoInteractions(sorties, administrations, voletsP, voletsM, journal);
        verify(seances, org.mockito.Mockito.never()).delete(any(), any());
    }
}
