package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.infrastructure.reporting.CahierDialyseReportService;
import com.hemodialyse.backend.infrastructure.reporting.ModeleDocumentPrinter;
import com.hemodialyse.backend.infrastructure.security.CenterAccessGuard;
import com.hemodialyse.backend.infrastructure.security.UserPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Impression du cahier : le centre vient du garde, le fuseau invalide retombe sur UTC, le PDF est servi en ligne et le
 * format du modèle (Excel) est respecté.
 */
class CahierDialyseRestControllerTest {

    private final CahierDialyseReportService rapport = mock(CahierDialyseReportService.class);
    private final CenterAccessGuard guard = mock(CenterAccessGuard.class);
    private final UUID centre = UUID.randomUUID();
    private final UUID patient = UUID.randomUUID();
    private final CahierDialyseRestController controller = new CahierDialyseRestController(rapport, guard);

    CahierDialyseRestControllerTest() {
        when(guard.requireCenter(any())).thenReturn(CenterId.of(centre));
    }

    private static Authentication auth() {
        UserPrincipal p = UserPrincipal.create(UUID.randomUUID().toString(), UUID.randomUUID().toString(), "infirmier1", "",
                List.of("INFIRMIER"), true);
        return new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities());
    }

    @Test
    void serves_the_pdf_inline_for_the_guarded_center_and_passes_the_period_and_user() {
        byte[] pdf = "%PDF-1.4".getBytes();
        LocalDate debut = LocalDate.of(2026, 9, 1);
        LocalDate fin = LocalDate.of(2026, 9, 30);
        when(rapport.imprimer(centre, patient, debut, fin, ZoneId.of("Africa/Algiers"), "infirmier1"))
                .thenReturn(new ModeleDocumentPrinter.Document(pdf, "PDF"));

        var response = controller.imprimer(patient, null, debut, fin, "Africa/Algiers", auth());

        assertEquals(MediaType.APPLICATION_PDF, response.getHeaders().getContentType());
        assertTrue(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION).startsWith("inline"));
        assertArrayEquals(pdf, response.getBody());
    }

    @Test
    void an_unknown_time_zone_falls_back_to_utc_and_an_excel_model_is_an_attachment() {
        when(rapport.imprimer(centre, patient, null, null, ZoneOffset.UTC, "infirmier1"))
                .thenReturn(new ModeleDocumentPrinter.Document(new byte[]{1}, "EXCEL"));

        var response = controller.imprimer(patient, null, null, null, "Nulle/Part", auth());

        assertTrue(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION).startsWith("attachment"));
        assertEquals(ZoneOffset.UTC, CahierDialyseRestController.zone(null));
    }
}
