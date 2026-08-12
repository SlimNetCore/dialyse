package com.hemodialyse.backend.application.stock;

import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.domain.stock.model.SortieRequestItem;
import com.hemodialyse.backend.domain.stock.port.BonSortieUseCase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class BonSortieApplicationServiceIntegrationTest {

    private static final UUID CENTER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID PATIENT_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID SEANCE_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID BON_ID = UUID.fromString("66666666-6666-6666-6666-666666666666");

    @Autowired
    private BonSortieUseCase bonSortieUseCase;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM bons_sortie_lignes WHERE bon_sortie_id = ?", BON_ID);
        jdbc.update("DELETE FROM bons_sortie WHERE id = ?", BON_ID);
        jdbc.update("DELETE FROM seances WHERE id = ?", SEANCE_ID);
    }

    @Test
    void update_should_reject_billed_seance_linked_stock_exit() {
        cleanup();

        jdbc.update(
                "INSERT INTO seances (id, patient_id, center_id, date_seance, statut, created_at) VALUES (?, ?, ?, ?, ?, ?)",
                SEANCE_ID,
                PATIENT_ID,
                CENTER_ID,
                LocalDate.of(2026, 8, 1),
                "FACTUREE",
                OffsetDateTime.now(ZoneOffset.UTC)
        );

        jdbc.update(
                "INSERT INTO bons_sortie (id, center_id, reference, seance_id, patient_id, poste, date_sortie, created_by, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                BON_ID,
                CENTER_ID,
                "BS-FACT-001",
                SEANCE_ID,
                PATIENT_ID,
                "SEANCE",
                LocalDate.of(2026, 8, 1),
                "inf-01",
                OffsetDateTime.now(ZoneOffset.UTC)
        );

        assertThrows(IllegalStateException.class, () -> bonSortieUseCase.update(
                CenterId.of(CENTER_ID),
                BON_ID,
                SEANCE_ID,
                PATIENT_ID,
                "SEANCE",
                LocalDate.of(2026, 8, 2),
                List.of(new SortieRequestItem(UUID.randomUUID(), UUID.randomUUID(), BigDecimal.ONE)),
                "inf-02"
        ));
    }
}

